package com.kreggscode.polliworld.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player UUID -> BYOP API key, granted through the device flow, with a plain
 * line-based serialisation for storage in keys.yml.
 */
public class KeyStore {

    private final Map<String, String> keys = new LinkedHashMap<>();

    public void put(String playerUuid, String key) {
        if (playerUuid != null && key != null && !key.isBlank()) {
            keys.put(playerUuid, key);
        }
    }

    public String get(String playerUuid) {
        String k = keys.get(playerUuid);
        return k == null || k.isBlank() ? null : k;
    }

    public void remove(String playerUuid) {
        keys.remove(playerUuid);
    }

    public int size() {
        return keys.size();
    }

    public String toLines() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : keys.entrySet()) {
            sb.append(e.getKey()).append(':').append(MemoryStore.escape(e.getValue())).append('\n');
        }
        return sb.toString();
    }

    public static KeyStore fromLines(String data) {
        KeyStore store = new KeyStore();
        if (data == null || data.isBlank()) {
            return store;
        }
        for (String line : data.split("\n", -1)) {
            int sep = line.indexOf(':');
            if (sep <= 0) {
                continue;
            }
            store.put(line.substring(0, sep).trim(), MemoryStore.unescape(line.substring(sep + 1).trim()));
        }
        return store;
    }
}
