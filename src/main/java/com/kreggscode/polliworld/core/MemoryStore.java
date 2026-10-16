package com.kreggscode.polliworld.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-NPC, per-player conversation memory with a bounded ring of messages and a
 * plain line-based serialisation so it can round-trip through a YAML file and
 * be unit-tested without a server.
 */
public class MemoryStore {

    public record Msg(long ts, String role, String content) {}

    private final int cap;
    private final Map<String, List<Msg>> mem = new LinkedHashMap<>();

    public MemoryStore(int cap) {
        this.cap = Math.max(2, cap);
    }

    private static String key(String npc, String player) {
        return npc + "|" + player;
    }

    public void add(String npc, String player, String role, String content) {
        List<Msg> list = mem.computeIfAbsent(key(npc, player), k -> new ArrayList<>());
        list.add(new Msg(System.currentTimeMillis(), role, content));
        while (list.size() > cap) {
            list.remove(0);
        }
    }

    /** The last {@code n} messages for this (npc, player), oldest first. */
    public List<Msg> recent(String npc, String player, int n) {
        List<Msg> list = mem.get(key(npc, player));
        if (list == null) {
            return List.of();
        }
        int from = Math.max(0, list.size() - Math.max(0, n));
        return new ArrayList<>(list.subList(from, list.size()));
    }

    public int size(String npc, String player) {
        List<Msg> list = mem.get(key(npc, player));
        return list == null ? 0 : list.size();
    }

    public void clear(String npc, String player) {
        mem.remove(key(npc, player));
    }

    public String toLines() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Msg>> e : mem.entrySet()) {
            sb.append(e.getKey()).append('\n');
            for (Msg m : e.getValue()) {
                sb.append(m.ts())
                        .append('|')
                        .append(escape(m.role()))
                        .append('|')
                        .append(escape(m.content()))
                        .append('\n');
            }
            sb.append(".\n");
        }
        return sb.toString();
    }

    public static MemoryStore fromLines(String data, int cap) {
        MemoryStore store = new MemoryStore(cap);
        if (data == null || data.isBlank()) {
            return store;
        }
        String current = null;
        for (String line : data.split("\n", -1)) {
            if (line.equals(".")) {
                current = null;
            } else if (current == null && !line.isBlank()) {
                current = line;
            } else if (current != null && !line.isBlank()) {
                int first = line.indexOf('|');
                int second = line.indexOf('|', first + 1);
                if (first < 0 || second < 0) {
                    continue;
                }
                long ts;
                try {
                    ts = Long.parseLong(line.substring(0, first));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                String role = unescape(line.substring(first + 1, second));
                String content = unescape(line.substring(second + 1));
                store.addRaw(current, ts, role, content);
            }
        }
        return store;
    }

    // Round-trip helpers used by fromLines: entries are keyed by "npc|player"
    // directly, so add() must not re-key. These package-private variants keep
    // the stored key intact.
    private void addRaw(String compoundKey, long ts, String role, String content) {
        List<Msg> list = mem.computeIfAbsent(compoundKey, k -> new ArrayList<>());
        list.add(new Msg(ts, role, content));
        while (list.size() > cap) {
            list.remove(0);
        }
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'p' -> out.append('|');
                    case 'n' -> out.append('\n');
                    case '\\' -> out.append('\\');
                    default -> out.append(n);
                }
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
