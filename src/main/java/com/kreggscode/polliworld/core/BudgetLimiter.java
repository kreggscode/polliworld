package com.kreggscode.polliworld.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player spending guards: a chat cooldown and a daily picture budget, with
 * line-based serialisation so budgets survive restarts.
 */
public class BudgetLimiter {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private final long chatCooldownMillis;
    private final int maxImagesPerDay;

    // player -> {lastChatMs, imagesToday, dayStamp}
    private final Map<String, long[]> state = new HashMap<>();

    public BudgetLimiter(long chatCooldownSeconds, int maxImagesPerDay) {
        this.chatCooldownMillis = Math.max(0, chatCooldownSeconds) * 1000L;
        this.maxImagesPerDay = Math.max(0, maxImagesPerDay);
    }

    private long[] entry(String player) {
        return state.computeIfAbsent(player, k -> new long[] {0L, 0L, 0L});
    }

    /** Null when allowed, otherwise the reason to show the player. */
    public String denyChatReason(String player, long now) {
        if (chatCooldownMillis == 0) {
            return null;
        }
        long last = entry(player)[0];
        long wait = last + chatCooldownMillis - now;
        return wait > 0 ? "Slow down — try again in " + ((wait + 999) / 1000) + "s." : null;
    }

    public void recordChat(String player, long now) {
        entry(player)[0] = now;
    }

    /** Null when allowed, otherwise the reason to show the player. */
    public String denyImageReason(String player, long now) {
        long[] e = entry(player);
        rollDay(e, now);
        if (maxImagesPerDay == 0) {
            return "Pictures are turned off on this server.";
        }
        long cooldownWait = chatCooldownMillis == 0 ? 0 : e[0] + chatCooldownMillis - now;
        if (cooldownWait > 0) {
            return "Slow down — try again in " + ((cooldownWait + 999) / 1000) + "s.";
        }
        if (e[1] >= maxImagesPerDay) {
            return "You reached today's picture budget (" + maxImagesPerDay + "). It resets after 24h.";
        }
        return null;
    }

    public void recordImage(String player, long now) {
        long[] e = entry(player);
        rollDay(e, now);
        e[1]++;
        e[0] = now;
    }

    public int imagesUsedToday(String player, long now) {
        long[] e = entry(player);
        rollDay(e, now);
        return (int) e[1];
    }

    private void rollDay(long[] e, long now) {
        if (e[2] == 0 || now - e[2] >= DAY) {
            e[1] = 0;
            e[2] = now;
        }
    }

    public String toLines() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, long[]> e : state.entrySet()) {
            sb.append(e.getKey()).append('|').append(e.getValue()[0]).append('|')
                    .append(e.getValue()[1]).append('|').append(e.getValue()[2]).append('\n');
        }
        return sb.toString();
    }

    public static BudgetLimiter fromLines(String data, long chatCooldownSeconds, int maxImagesPerDay) {
        BudgetLimiter limiter = new BudgetLimiter(chatCooldownSeconds, maxImagesPerDay);
        if (data == null || data.isBlank()) {
            return limiter;
        }
        for (String line : data.split("\n", -1)) {
            String[] parts = line.split("\\|");
            if (parts.length != 4) {
                continue;
            }
            try {
                limiter.state.put(parts[0], new long[] {
                    Long.parseLong(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3])});
            } catch (NumberFormatException ignored) {
                // skip corrupt lines
            }
        }
        return limiter;
    }
}
