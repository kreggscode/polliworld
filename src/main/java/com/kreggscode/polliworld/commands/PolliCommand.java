package com.kreggscode.polliworld.commands;

import com.kreggscode.polliworld.PolliWorld;
import com.kreggscode.polliworld.core.DeviceFlow;
import com.kreggscode.polliworld.core.MemoryStore.Msg;
import com.kreggscode.polliworld.core.PollinationsApi;
import com.kreggscode.polliworld.map.MapArt;
import com.kreggscode.polliworld.map.MapPainter;
import com.kreggscode.polliworld.npc.NpcManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /polli ask · image · models · npc · say · bye · signin · status · help
 */
public class PolliCommand implements CommandExecutor, TabCompleter {

    private static final String ASK_NPC = "(ask)";
    private static final List<String> SUBS = List.of("ask", "image", "models", "npc", "say", "bye", "signin", "status", "help");

    private final PolliWorld plugin;

    public PolliCommand(PolliWorld plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 0) {
                help(player);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "ask" -> ask(player, rest(args));
                case "image" -> image(player, rest(args));
                case "models" -> models(player);
                case "npc" -> npc(player, args);
                case "say" -> say(player, args);
                case "bye" -> plugin.sessionManager().end(player);
                case "signin" -> signin(player);
                case "status" -> status(player);
                case "help" -> help(player);
                default -> help(player);
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            // Console or RCON: same server-key commands.
            console(sender, args);
            return true;
        }
        return true;
    }

    /** Console support: /polli ask|image|models|npc run with the server's key. */
    private void console(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /polli <ask|image|models|npc|status|help>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "ask" -> consoleAsk(sender, rest(args));
            case "image" -> consoleImage(sender, rest(args));
            case "models" -> consoleModels(sender);
            case "npc" -> consoleNpc(sender, args);
            case "status" -> sender.sendMessage("[PolliWorld] key: " + (plugin.cfg().apiKey().isBlank() ? "none" : "server key set")
                    + " · text " + plugin.cfg().textModel() + " · image " + plugin.cfg().imageModel());
            case "help" -> sender.sendMessage("Usage: /polli <ask|image|models|npc create <name> [persona]|status>");
            default -> sender.sendMessage("Unknown subcommand: " + args[0]);
        }
    }

    private void consoleAsk(CommandSender sender, String question) {
        if (question.isEmpty()) {
            sender.sendMessage("Usage: /polli ask <question>");
            return;
        }
        String key = plugin.cfg().apiKey();
        if (key.isBlank()) {
            sender.sendMessage("No api-key in config.yml.");
            return;
        }
        String model = plugin.cfg().textModel();
        sender.sendMessage("Asking " + model + "…");
        plugin.pool().submit(() -> {
            try {
                PollinationsApi.Completion result = PollinationsApi.chat(
                        key, model,
                        "You are a helpful assistant for players on a Minecraft server. Answer in one to three short, friendly sentences.",
                        plugin.memory().recent("(console)", "console", 12), question);
                plugin.memory().add("(console)", "console", "user", question);
                plugin.memory().add("(console)", "console", "assistant", result.content());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(result.content());
                    sender.sendMessage("· " + result.inTokens() + " in / " + result.outTokens() + " out tokens · " + model);
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("Ask failed: " + e.getMessage()));
            }
        });
    }

    private void consoleModels(CommandSender sender) {
        String key = plugin.cfg().apiKey();
        plugin.pool().submit(() -> {
            try {
                List<PollinationsApi.ModelInfo> list = PollinationsApi.listModels(key);
                long free = list.stream().filter(m -> !m.paidOnly()).count();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("Live text models: " + list.size() + " (" + free + " free, "
                            + (list.size() - free) + " Paid Pollen)");
                    sender.sendMessage("First free: " + trimNames(
                            list.stream().filter(m -> !m.paidOnly()).map(PollinationsApi.ModelInfo::name).toList()));
                    sender.sendMessage("First paid: " + trimNames(
                            list.stream().filter(PollinationsApi.ModelInfo::paidOnly).map(PollinationsApi.ModelInfo::name).toList()));
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("Model list failed: " + e.getMessage()));
            }
        });
    }

    private void consoleImage(CommandSender sender, String prompt) {
        if (prompt.isEmpty()) {
            sender.sendMessage("Usage: /polli image <prompt>");
            return;
        }
        String key = plugin.cfg().apiKey();
        if (key.isBlank()) {
            sender.sendMessage("No api-key in config.yml.");
            return;
        }
        String model = plugin.cfg().imageModel();
        sender.sendMessage("Painting a picture with " + model + "…");
        plugin.pool().submit(() -> {
            try {
                byte[] bytes = PollinationsApi.image(key, prompt, model, MapArt.SIZE, MapArt.SIZE);
                BufferedImage art = MapArt.fit(MapArt.decode(bytes));
                File dir = new File(plugin.getDataFolder(), "images");
                Files.createDirectories(dir.toPath());
                File out = new File(dir, "console-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png");
                ImageIO.write(art, "png", out);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage("Picture saved: " + out.getPath()));
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("Image failed: " + e.getMessage()));
            }
        });
    }

    private void consoleNpc(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("create") && args.length >= 3) {
            String name = args[2];
            if (plugin.npcManager().exists(name)) {
                sender.sendMessage("There already is an NPC named " + name + ".");
                return;
            }
            String persona = args.length > 3
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                    : "a wise and friendly villager who loves talking about the server";
            World world = plugin.getServer().getWorld("world");
            if (world == null) {
                sender.sendMessage("World 'world' not found.");
                return;
            }
            Location spawn = world.getSpawnLocation();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.npcManager().define(name, persona, spawn);
                sender.sendMessage("NPC " + name + " spawned at world spawn with persona: " + persona);
            });
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove") && args.length >= 3) {
            boolean ok = plugin.npcManager().remove(args[2]);
            sender.sendMessage(ok ? "NPC " + args[2] + " removed." : "No NPC named " + args[2] + ".");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            var all = plugin.npcManager().all();
            if (all.isEmpty()) {
                sender.sendMessage("No NPCs yet.");
                return;
            }
            for (NpcManager.Definition d : all.values()) {
                sender.sendMessage(d.name() + " — " + d.persona() + " @ " + d.world());
            }
            return;
        }
        sender.sendMessage("Usage: /polli npc create <name> [persona] | remove <name> | list");
    }

    private static String rest(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------- ask

    private void ask(Player player, String question) {
        if (question.isEmpty()) {
            player.sendMessage(Component.text("Usage: /polli ask <question>", NamedTextColor.RED));
            return;
        }
        String deny = plugin.budget().denyChatReason(player.getUniqueId().toString(), System.currentTimeMillis());
        if (deny != null) {
            player.sendMessage(Component.text(deny, NamedTextColor.RED));
            return;
        }
        String key = plugin.apiKeyFor(player);
        if (key == null || key.isBlank()) {
            player.sendMessage(Component.text("No Pollen key: run /polli signin to use your own Pollen.", NamedTextColor.RED));
            return;
        }
        String model = plugin.cfg().textModel();
        String system = "You are a helpful assistant for players on a Minecraft server. "
                + "Answer in one to three short, friendly sentences.";
        List<Msg> history = plugin.memory().recent(ASK_NPC, player.getUniqueId().toString(), 12);
        plugin.budget().recordChat(player.getUniqueId().toString(), System.currentTimeMillis());
        player.sendMessage(Component.text("Asking " + model + "…", NamedTextColor.DARK_GRAY));

        plugin.pool().submit(() -> {
            try {
                PollinationsApi.Completion result = PollinationsApi.chat(key, model, system, history, question);
                plugin.memory().add(ASK_NPC, player.getUniqueId().toString(), "user", question);
                plugin.memory().add(ASK_NPC, player.getUniqueId().toString(), "assistant", result.content());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(Component.text(result.content(), NamedTextColor.WHITE));
                    player.sendMessage(Component.text(
                            "· " + result.inTokens() + " in / " + result.outTokens() + " out tokens · " + model
                                    + (plugin.hasOwnKey(player) ? " · your Pollen" : " · server key"),
                            NamedTextColor.DARK_GRAY));
                });
            } catch (Exception e) {
                sendErr(player, e);
            }
        });
    }

    // ------------------------------------------------------------------ image

    private void image(Player player, String prompt) {
        if (prompt.isEmpty()) {
            player.sendMessage(Component.text("Usage: /polli image <prompt>", NamedTextColor.RED));
            return;
        }
        String uuid = player.getUniqueId().toString();
        long now = System.currentTimeMillis();
        String deny = plugin.budget().denyImageReason(uuid, now);
        if (deny != null) {
            player.sendMessage(Component.text(deny, NamedTextColor.RED));
            return;
        }
        String key = plugin.apiKeyFor(player);
        if (key == null || key.isBlank()) {
            player.sendMessage(Component.text("No Pollen key: run /polli signin to use your own Pollen.", NamedTextColor.RED));
            return;
        }
        String model = plugin.cfg().imageModel();
        player.sendMessage(Component.text("Painting a picture with " + model + "…", NamedTextColor.DARK_GRAY));
        Location loc = player.getLocation();

        plugin.pool().submit(() -> {
            try {
                byte[] bytes = PollinationsApi.image(key, prompt, model, MapArt.SIZE, MapArt.SIZE);
                BufferedImage art = MapArt.fit(MapArt.decode(bytes));
                savePng(plugin, prompt, art);
                plugin.budget().recordImage(uuid, System.currentTimeMillis());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    ItemStack map = MapPainter.paint(loc.getWorld(), art);
                    if (map == null) {
                        player.sendMessage(Component.text("Could not paint the map.", NamedTextColor.RED));
                        return;
                    }
                    player.getInventory().addItem(map);
                    player.sendMessage(Component.text("Here is your picture, painted on a real map item.", NamedTextColor.GREEN));
                    player.sendMessage(Component.text(
                            "· saved under plugins/PolliWorld/images · " + model
                                    + (plugin.hasOwnKey(player) ? " · your Pollen" : " · server key"),
                            NamedTextColor.DARK_GRAY));
                });
            } catch (Exception e) {
                sendErr(player, e);
            }
        });
    }

    private static void savePng(JavaPlugin plugin, String prompt, BufferedImage art) {
        try {
            File dir = new File(plugin.getDataFolder(), "images");
            Files.createDirectories(dir.toPath());
            String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + "-" + prompt.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("-+", "-").toLowerCase(Locale.ROOT);
            if (name.length() > 60) {
                name = name.substring(0, 60);
            }
            ImageIO.write(art, "png", new File(dir, name + ".png"));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save the picture: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------- models

    private void models(Player player) {
        String key = plugin.apiKeyFor(player);
        player.sendMessage(Component.text("Reading the live model list…", NamedTextColor.DARK_GRAY));
        plugin.pool().submit(() -> {
            try {
                List<PollinationsApi.ModelInfo> list = PollinationsApi.listModels(key);
                List<String> free = new ArrayList<>();
                List<String> paid = new ArrayList<>();
                for (PollinationsApi.ModelInfo m : list) {
                    (m.paidOnly() ? paid : free).add(m.name());
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(Component.text("Free models (your own free Pollen works):", NamedTextColor.GREEN));
                    player.sendMessage(Component.text(trimNames(free), NamedTextColor.GRAY));
                    player.sendMessage(Component.text("Paid Pollen models:", NamedTextColor.GOLD));
                    player.sendMessage(Component.text(trimNames(paid), NamedTextColor.GRAY));
                    player.sendMessage(Component.text(
                            "Current: " + plugin.cfg().textModel() + " / " + plugin.cfg().imageModel()
                                    + " — admins set them in config.yml.",
                            NamedTextColor.DARK_GRAY));
                });
            } catch (Exception e) {
                sendErr(player, e);
            }
        });
    }

    private static String trimNames(List<String> names) {
        int shown = Math.min(names.size(), 12);
        String s = String.join(", ", names.subList(0, shown));
        return names.size() > shown ? s + ", … (" + names.size() + " total)" : s;
    }

    // -------------------------------------------------------------------- npc

    private void npc(Player player, String[] args) {
        if (!player.hasPermission("polliworld.admin")) {
            player.sendMessage(Component.text("Creating NPCs needs polliworld.admin (op).", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /polli npc create <name> [persona] | remove <name> | list", NamedTextColor.RED));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /polli npc create <name> [persona]", NamedTextColor.RED));
                    return;
                }
                String name = args[2];
                if (plugin.npcManager().exists(name)) {
                    player.sendMessage(Component.text("There already is an NPC named " + name + ".", NamedTextColor.RED));
                    return;
                }
                String persona = args.length > 3
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                        : "a wise and friendly villager who loves talking about the server";
                plugin.npcManager().define(name, persona, player.getLocation());
                player.sendMessage(Component.text(
                        "NPC " + name + " spawned here with its persona. Right-click it to talk.", NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /polli npc remove <name>", NamedTextColor.RED));
                    return;
                }
                boolean ok = plugin.npcManager().remove(args[2]);
                player.sendMessage(ok
                        ? Component.text("NPC " + args[2] + " removed.", NamedTextColor.GREEN)
                        : Component.text("No NPC named " + args[2] + ".", NamedTextColor.RED));
            }
            case "list" -> {
                var all = plugin.npcManager().all();
                if (all.isEmpty()) {
                    player.sendMessage(Component.text("No NPCs yet: /polli npc create <name> [persona]", NamedTextColor.GRAY));
                    return;
                }
                for (NpcManager.Definition d : all.values()) {
                    player.sendMessage(Component.text(
                            d.name() + " — " + d.persona() + " @ " + d.world(),
                            NamedTextColor.YELLOW));
                }
            }
            default -> player.sendMessage(Component.text("Usage: /polli npc create|remove|list", NamedTextColor.RED));
        }
    }

    private void say(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /polli say <npc> <message>", NamedTextColor.RED));
            return;
        }
        String npcName = args[1];
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        plugin.sessionManager().say(player, npcName, message);
    }

    // ----------------------------------------------------------------- signin

    private void signin(Player player) {
        if (!plugin.cfg().byopEnabled()) {
            player.sendMessage(Component.text("BYOP sign-in is turned off on this server.", NamedTextColor.RED));
            return;
        }
        String key = plugin.apiKeyFor(player);
        if (plugin.hasOwnKey(player)) {
            player.sendMessage(Component.text(
                    "You already signed in — your commands bill to your own Pollen. /polli status for details.",
                    NamedTextColor.GREEN));
            return;
        }
        if (key == null || key.isBlank()) {
            // No server key either; the visitor still needs a key from somewhere.
        }
        player.sendMessage(Component.text("Starting device sign-in…", NamedTextColor.DARK_GRAY));

        UUID uuid = player.getUniqueId();
        plugin.pool().submit(() -> {
            try {
                DeviceFlow.Start start = DeviceFlow.parseStart(postJson(DeviceFlow.START_URL, DeviceFlow.startJson("api:read api:write")));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(Component.text("Sign in with your OWN Pollen:", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Open ", NamedTextColor.WHITE)
                            .append(Component.text(start.verificationUriComplete(), NamedTextColor.AQUA)
                                    .decoration(TextDecoration.UNDERLINED, true)
                                    .clickEvent(ClickEvent.openUrl(start.verificationUriComplete())))
                            .append(Component.text(" and approve code " + start.userCode() + ".", NamedTextColor.WHITE)));
                    player.sendMessage(Component.text(
                            "Your commands will bill to your own Pollen until the key expires. Decline in the browser to cancel.",
                            NamedTextColor.GRAY));
                });

                long deadline = System.currentTimeMillis() + plugin.cfg().byopExpireSeconds() * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(plugin.cfg().byopPollSeconds() * 1000L);
                    String response = postJson(DeviceFlow.TOKEN_URL, DeviceFlow.tokenJson(start.deviceCode()));
                    try {
                        String visitorKey = DeviceFlow.parseToken(response);
                        plugin.keyStore().put(uuid.toString(), visitorKey);
                        plugin.saveKeys();
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(Component.text(
                                        "Signed in — your commands now use your own Pollen. Welcome aboard!",
                                        NamedTextColor.GREEN));
                            }
                        });
                        return;
                    } catch (DeviceFlow.PendingException pending) {
                        // keep polling
                    }
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("The sign-in code expired — try /polli signin again.", NamedTextColor.RED));
                    }
                });
            } catch (Exception e) {
                sendErr(player, e);
            }
        });
    }

    private static String postJson(String url, String body) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build();
        java.net.http.HttpResponse<String> res =
                client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new DeviceFlow.FailedException("Sign-in service answered HTTP " + res.statusCode() + ".");
        }
        return res.body();
    }

    // ----------------------------------------------------------------- status

    private void status(Player player) {
        boolean own = plugin.hasOwnKey(player);
        player.sendMessage(Component.text("PolliWorld status", NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                "Key: " + (own ? "your own Pollen (signed in via /polli signin)"
                        : (plugin.cfg().apiKey().isBlank() ? "none — run /polli signin" : "the server's key")),
                NamedTextColor.WHITE));
        player.sendMessage(Component.text(
                "Models: text " + plugin.cfg().textModel() + " · image " + plugin.cfg().imageModel(), NamedTextColor.WHITE));
        int used = plugin.budget().imagesUsedToday(player.getUniqueId().toString(), System.currentTimeMillis());
        player.sendMessage(Component.text(
                "Pictures used today: " + used + " / " + plugin.cfg().maxImagesPerDay(), NamedTextColor.WHITE));
        String npc = plugin.sessionManager().activeNpc(player);
        player.sendMessage(Component.text(
                "Talking to: " + (npc == null ? "nobody (right-click an NPC or /polli say <npc> <msg>)" : npc),
                NamedTextColor.WHITE));
    }

    private void help(Player player) {
        player.sendMessage(Component.text("PolliWorld — Pollinations on this server", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/polli ask <question> — ask the AI, billed to your key or the server's", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/polli image <prompt> — a picture on a real map item", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/polli models — the live free and paid model lists", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/polli npc create <name> [persona] — spawn a talking villager (op)", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/polli say <npc> <msg> — ask an NPC directly; right-click to chat in person", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/polli bye — leave the conversation · /polli status — your settings", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/polli signin — pay with your OWN Pollen (BYOP device sign-in)", NamedTextColor.WHITE));
    }

    private void sendErr(Player player, Exception e) {
        plugin.getLogger().warning("PolliWorld error for " + player.getName() + ": " + e.getMessage());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(Component.text("Pollinations call failed: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    // ------------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return filter(List.of("create", "remove", "list"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("say"))) {
            return filter(new ArrayList<>(plugin.npcManager().all().keySet()), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
