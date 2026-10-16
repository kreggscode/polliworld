package com.kreggscode.polliworld.npc;

import com.kreggscode.polliworld.PolliWorld;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Villager NPCs with personas: definitions live in npcs.yml, the villagers
 * themselves are non-persistent and respawned at boot, so a restart restores
 * the cast without duplicating entities.
 */
public class NpcManager implements Listener {

    public record Definition(String name, String persona, String world, double x, double y, double z) {}

    private final PolliWorld plugin;
    private final Map<String, Definition> definitions = new HashMap<>();
    private final Map<UUID, String> entities = new HashMap<>();

    public NpcManager(PolliWorld plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------- definitions

    public void loadDefinitions() {
        definitions.clear();
        File f = new File(plugin.getDataFolder(), "npcs.yml");
        if (!f.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection npcs = yml.getConfigurationSection("npcs");
        if (npcs == null) {
            return;
        }
        for (String key : npcs.getKeys(false)) {
            ConfigurationSection s = npcs.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            definitions.put(key, new Definition(
                    key,
                    s.getString("persona", "a friendly villager"),
                    s.getString("world", "world"),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " NPC definition(s).");
    }

    public void saveDefinitions() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Definition d : definitions.values()) {
            String path = "npcs." + d.name();
            yml.set(path + ".persona", d.persona());
            yml.set(path + ".world", d.world());
            yml.set(path + ".x", d.x());
            yml.set(path + ".y", d.y());
            yml.set(path + ".z", d.z());
        }
        try {
            yml.save(new File(plugin.getDataFolder(), "npcs.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save npcs.yml: " + e.getMessage());
        }
    }

    public boolean exists(String name) {
        return definitions.containsKey(name);
    }

    public Definition get(String name) {
        return definitions.get(name);
    }

    public Map<String, Definition> all() {
        return Map.copyOf(definitions);
    }

    public void define(String name, String persona, Location loc) {
        definitions.put(name, new Definition(
                name, persona, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
        saveDefinitions();
        spawnOne(definitions.get(name));
    }

    public boolean remove(String name) {
        Definition d = definitions.remove(name);
        if (d == null) {
            return false;
        }
        entities.entrySet().removeIf(e -> e.getValue().equals(name));
        saveDefinitions();
        return true;
    }

    // ------------------------------------------------------------------ spawns

    public void spawnAll() {
        for (Definition d : definitions.values()) {
            spawnOne(d);
        }
    }

    private void spawnOne(Definition d) {
        World world = plugin.getServer().getWorld(d.world());
        if (world == null) {
            plugin.getLogger().warning("World " + d.world() + " not found, NPC " + d.name() + " not spawned.");
            return;
        }
        Location loc = new Location(world, d.x(), d.y(), d.z());
        Villager villager = world.spawn(loc, Villager.class, v -> {
            v.setCustomName(d.name());
            v.setCustomNameVisible(true);
            v.setAI(false);
            v.setPersistent(false);
            v.setProfession(Villager.Profession.NONE);
        });
        entities.put(villager.getUniqueId(), d.name());
    }

    // ---------------------------------------------------------------- listeners

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        String npcName = entities.get(event.getRightClicked().getUniqueId());
        if (npcName == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("polliworld.use")) {
            return;
        }
        plugin.sessionManager().start(player, npcName);
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        if (entities.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
