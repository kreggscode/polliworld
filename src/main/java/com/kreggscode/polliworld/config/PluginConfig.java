package com.kreggscode.polliworld.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view over config.yml so the rest of the plugin never touches strings.
 */
public class PluginConfig {

    private final FileConfiguration c;

    public PluginConfig(FileConfiguration c) {
        this.c = c;
    }

    public String apiKey() {
        return trim(c.getString("api-key", ""));
    }

    public String textModel() {
        return trim(c.getString("text-model", "openai/gpt-5.4-nano"));
    }

    public String imageModel() {
        return trim(c.getString("image-model", "flux"));
    }

    public int npcMemoryMessages() {
        return Math.max(2, c.getInt("npc.memory-messages", 24));
    }

    public int sessionTimeoutSeconds() {
        return Math.max(10, c.getInt("npc.session-timeout-seconds", 90));
    }

    public long chatCooldownSeconds() {
        return Math.max(0, c.getInt("limits.chat-cooldown-seconds", 2));
    }

    public int maxImagesPerDay() {
        return Math.max(0, c.getInt("limits.max-images-per-day", 20));
    }

    public boolean byopEnabled() {
        return c.getBoolean("byop.enabled", true);
    }

    public int byopPollSeconds() {
        return Math.max(3, c.getInt("byop.poll-seconds", 5));
    }

    public int byopExpireSeconds() {
        return Math.max(60, c.getInt("byop.expire-seconds", 1800));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
