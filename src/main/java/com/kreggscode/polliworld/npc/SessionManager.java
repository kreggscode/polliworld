package com.kreggscode.polliworld.npc;

import com.kreggscode.polliworld.PolliWorld;
import com.kreggscode.polliworld.core.MemoryStore.Msg;
import com.kreggscode.polliworld.core.PollinationsApi;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * While a player is "in a conversation" with an NPC their chat messages are
 * routed to Pollinations instead of public chat: the NPC answers in its
 * persona, with per-player memory, until /polli bye or a timeout.
 */
public class SessionManager implements Listener {

    private record Session(String npc, long expiresAt) {}

    private final PolliWorld plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(PolliWorld plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        sessions.clear();
    }

    public boolean inSession(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return false;
        }
        if (System.currentTimeMillis() > s.expiresAt()) {
            sessions.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public String activeNpc(Player player) {
        return inSession(player) ? sessions.get(player.getUniqueId()).npc() : null;
    }

    public void start(Player player, String npcName) {
        NpcManager.Definition def = plugin.npcManager().get(npcName);
        if (def == null) {
            player.sendMessage(Component.text("No NPC named " + npcName + ".", NamedTextColor.RED));
            return;
        }
        if (!plugin.hasOwnKey(player) && plugin.cfg().apiKey().isBlank()) {
            player.sendMessage(Component.text(
                    "This server has no Pollen key yet — run /polli signin to talk with your own Pollen.",
                    NamedTextColor.RED));
            return;
        }
        sessions.put(player.getUniqueId(), new Session(npcName, nextExpiry()));
        player.sendMessage(Component.text("You are now talking to " + npcName + ". Type in chat; /polli bye to leave.", NamedTextColor.YELLOW));
        askNpc(player, npcName, def, "Greet " + player.getName() + " briefly, in one short sentence, in character.", false);
    }

    public void end(Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("Conversation over.", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("You are not talking to anyone.", NamedTextColor.GRAY));
        }
    }

    /** /polli say — ask an NPC directly without being in a chat session. */
    public void say(Player player, String npcName, String message) {
        NpcManager.Definition def = plugin.npcManager().get(npcName);
        if (def == null) {
            player.sendMessage(Component.text("No NPC named " + npcName + ".", NamedTextColor.RED));
            return;
        }
        if (!plugin.hasOwnKey(player) && plugin.cfg().apiKey().isBlank()) {
            player.sendMessage(Component.text(
                    "This server has no Pollen key yet — run /polli signin to ask with your own Pollen.",
                    NamedTextColor.RED));
            return;
        }
        askNpc(player, npcName, def, message, true);
    }

    private long nextExpiry() {
        return System.currentTimeMillis() + plugin.cfg().sessionTimeoutSeconds() * 1000L;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String npc = activeNpc(player);
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (text.isBlank()) {
            return;
        }
        sessions.put(player.getUniqueId(), new Session(npc, nextExpiry()));
        NpcManager.Definition def = plugin.npcManager().get(npc);
        if (def == null) {
            sessions.remove(player.getUniqueId());
            return;
        }
        askNpc(player, npc, def, text, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    /** Runs the call off the main thread, then prints the reply on it. */
    private void askNpc(Player player, String npcName, NpcManager.Definition def, String message, boolean echo) {
        String key = plugin.apiKeyFor(player);
        String model = plugin.cfg().textModel();
        String system = "You are " + npcName + ", a villager NPC on a Minecraft server. Persona: " + def.persona()
                + ". Stay in character and keep replies to one or two short sentences.";
        List<Msg> history = plugin.memory().recent(npcName, player.getUniqueId().toString(), 16);

        if (echo) {
            player.sendMessage(Component.text("[→ " + npcName + "] ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName() + ": " + message, NamedTextColor.GRAY)));
        }

        plugin.pool().submit(() -> {
            try {
                PollinationsApi.Completion result =
                        PollinationsApi.chat(key, model, system, history, message);
                plugin.memory().add(npcName, player.getUniqueId().toString(), "user", message);
                plugin.memory().add(npcName, player.getUniqueId().toString(), "assistant", result.content());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(Component.text(npcName + ": ", NamedTextColor.YELLOW)
                            .append(Component.text(result.content(), NamedTextColor.WHITE)));
                    player.sendMessage(Component.text(
                            "· " + result.inTokens() + " in / " + result.outTokens() + " out tokens · " + model,
                            NamedTextColor.DARK_GRAY));
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(Component.text(npcName + " could not answer: " + e.getMessage(), NamedTextColor.RED));
                    }
                });
            }
        });
    }
}
