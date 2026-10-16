package com.kreggscode.polliworld;

import com.kreggscode.polliworld.commands.PolliCommand;
import com.kreggscode.polliworld.config.PluginConfig;
import com.kreggscode.polliworld.core.BudgetLimiter;
import com.kreggscode.polliworld.core.KeyStore;
import com.kreggscode.polliworld.core.MemoryStore;
import com.kreggscode.polliworld.npc.NpcManager;
import com.kreggscode.polliworld.npc.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PolliWorld — Pollinations AI for Minecraft servers.
 * /polli ask · /polli image · /polli models · NPC villagers · BYOP device sign-in.
 */
public final class PolliWorld extends JavaPlugin {

    private PluginConfig cfg;
    private MemoryStore memory;
    private KeyStore keyStore;
    private BudgetLimiter budget;
    private NpcManager npcManager;
    private SessionManager sessionManager;
    private ExecutorService pool;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new PluginConfig(getConfig());
        memory = new MemoryStore(cfg.npcMemoryMessages());
        keyStore = KeyStore.fromLines(readState("keys.yml"));
        budget = BudgetLimiter.fromLines(readState("budget.txt"), cfg.chatCooldownSeconds(), cfg.maxImagesPerDay());
        pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "PolliWorld-http");
            t.setDaemon(true);
            return t;
        });

        sessionManager = new SessionManager(this);
        npcManager = new NpcManager(this);
        npcManager.loadDefinitions();

        PolliCommand command = new PolliCommand(this);
        getCommand("polli").setExecutor(command);
        getCommand("polli").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(sessionManager, this);
        getServer().getPluginManager().registerEvents(npcManager, this);

        // Spawn NPC villagers once the worlds are up.
        getServer().getScheduler().runTask(this, npcManager::spawnAll);

        if (cfg.apiKey().isBlank()) {
            getLogger().warning("No api-key in config.yml — /polli will only work after players sign in with /polli signin (BYOP) or you add your key from enter.pollinations.ai.");
        }
    }

    @Override
    public void onDisable() {
        saveState("keys.yml", keyStore.toLines());
        saveState("budget.txt", budget.toLines());
        if (npcManager != null) {
            npcManager.saveDefinitions();
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ wiring

    public PluginConfig cfg() {
        return cfg;
    }

    public MemoryStore memory() {
        return memory;
    }

    public KeyStore keyStore() {
        return keyStore;
    }

    public BudgetLimiter budget() {
        return budget;
    }

    public NpcManager npcManager() {
        return npcManager;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public ExecutorService pool() {
        return pool;
    }

    /** The key this player's calls bill to: their own Pollen if signed in, else the server's. */
    public String apiKeyFor(Player player) {
        String own = keyStore.get(player.getUniqueId().toString());
        return own != null ? own : cfg.apiKey();
    }

    public boolean hasOwnKey(Player player) {
        return keyStore.get(player.getUniqueId().toString()) != null;
    }

    public void saveKeys() {
        saveState("keys.yml", keyStore.toLines());
    }

    // ------------------------------------------------------------------- state

    private Path stateFile(String name) {
        return new java.io.File(getDataFolder(), name).toPath();
    }

    private String readState(String name) {
        try {
            Path p = stateFile(name);
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            getLogger().warning("Could not read " + name + ": " + e.getMessage());
        }
        return "";
    }

    public void saveState(String name, String content) {
        try {
            Files.createDirectories(stateFile(name).getParent());
            Files.writeString(stateFile(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("Could not save " + name + ": " + e.getMessage());
        }
    }
}
