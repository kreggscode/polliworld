package com.kreggscode.polliworld.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kreggscode.polliworld.core.MemoryStore.Msg;

import java.util.List;

/**
 * Builds OpenAI-compatible chat-completions request bodies for
 * POST https://gen.pollinations.ai/v1/chat/completions.
 */
public final class RequestBuilder {

    private RequestBuilder() {}

    public static String chatJson(String model, String system, List<Msg> history, String userMessage) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        JsonArray messages = new JsonArray();
        if (system != null && !system.isBlank()) {
            messages.add(msg("system", system));
        }
        for (Msg m : history) {
            // The API only accepts system/user/assistant roles.
            String role = m.role().equals("user") || m.role().equals("assistant") ? m.role() : "user";
            messages.add(msg(role, m.content()));
        }
        messages.add(msg("user", userMessage));
        root.add("messages", messages);
        return root.toString();
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
