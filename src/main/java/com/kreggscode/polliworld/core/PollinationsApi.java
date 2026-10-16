package com.kreggscode.polliworld.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kreggscode.polliworld.core.MemoryStore.Msg;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for gen.pollinations.ai: chat completions, the image endpoint and
 * the live text-model list. Parsing is split into pure static methods so the
 * tricky parts are unit-tested without network.
 */
public final class PollinationsApi {

    public static final String GEN = "https://gen.pollinations.ai";

    public record Completion(String content, long inTokens, long outTokens) {}

    public record ModelInfo(String name, boolean paidOnly, String description) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private PollinationsApi() {}

    // ---------------------------------------------------------------- parsing

    public static Completion parseCompletion(String json) throws IOException {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = o.getAsJsonArray("choices");
            String content = "";
            if (choices != null && choices.size() > 0) {
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (message != null && message.has("content") && message.get("content").isJsonPrimitive()) {
                    content = message.get("content").getAsString();
                }
            }
            long in = 0;
            long out = 0;
            if (o.has("usage") && o.get("usage").isJsonObject()) {
                JsonObject usage = o.getAsJsonObject("usage");
                if (usage.has("prompt_tokens")) {
                    in = usage.get("prompt_tokens").getAsLong();
                }
                if (usage.has("completion_tokens")) {
                    out = usage.get("completion_tokens").getAsLong();
                }
            }
            if (content.isBlank()) {
                throw new IOException("The model returned no text.");
            }
            return new Completion(content.trim(), in, out);
        } catch (RuntimeException e) {
            throw new IOException("Could not read the completion response: " + e.getMessage());
        }
    }

    public static List<ModelInfo> parseModels(String json) throws IOException {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            List<ModelInfo> out = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject m = el.getAsJsonObject();
                if (!m.has("name")) {
                    continue;
                }
                String desc = m.has("description") && m.get("description").isJsonPrimitive()
                        ? m.get("description").getAsString()
                        : "";
                boolean paid = m.has("paid_only") && m.get("paid_only").getAsBoolean();
                out.add(new ModelInfo(m.get("name").getAsString(), paid, desc));
            }
            return out;
        } catch (RuntimeException e) {
            throw new IOException("Could not read the model list: " + e.getMessage());
        }
    }

    /** Extracts a short error message from a non-2xx JSON error body. */
    public static String errorMessage(String json, int status) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("error")) {
                JsonElement e = o.get("error");
                if (e.isJsonObject() && e.getAsJsonObject().has("message")) {
                    return e.getAsJsonObject().get("message").getAsString();
                }
                if (e.isJsonPrimitive()) {
                    return e.getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the status text below
        }
        return "HTTP " + status;
    }

    // ------------------------------------------------------------------- http

    public static Completion chat(String apiKey, String model, String system, List<Msg> history, String userMessage)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(RequestBuilder.chatJson(model, system, history, userMessage)))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            throw new IOException("Pollinations is rate limiting us for a moment — try again in a few seconds.");
        }
        if (res.statusCode() == 402) {
            throw new IOException("The model " + model + " needs Paid Pollen. Top up at enter.pollinations.ai or pick a free model with /polli models.");
        }
        if (res.statusCode() != 200) {
            throw new IOException("Text call failed: " + errorMessage(res.body(), res.statusCode()));
        }
        return parseCompletion(res.body());
    }

    public static byte[] image(String apiKey, String prompt, String model, int width, int height)
            throws IOException, InterruptedException {
        // The image API is OpenAI-compatible: POST /v1/images/generations with
        // a b64_json response (the old GET /<prompt> endpoint is gone).
        String body = "{\"model\":\"" + model.replace("\"", "") + "\",\"prompt\":\""
                + prompt.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"n\":1,\"size\":\""
                + width + "x" + height + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN + "/v1/images/generations"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            throw new IOException("Pollinations is rate limiting image generation — try again in a few seconds.");
        }
        if (res.statusCode() == 402) {
            throw new IOException("The image model " + model + " needs Paid Pollen. Top up at enter.pollinations.ai or pick a free model with /polli models.");
        }
        if (res.statusCode() != 200) {
            throw new IOException("Image call failed: " + errorMessage(res.body(), res.statusCode()));
        }
        byte[] bytes = parseImageResponse(res.body());
        if (bytes.length < 100) {
            throw new IOException("The image endpoint returned almost nothing — try another prompt.");
        }
        return bytes;
    }

    /** Reads data[0].b64_json (or data[0].url is left to fail loudly). */
    static byte[] parseImageResponse(String json) throws IOException {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = o.getAsJsonArray("data");
            if (data == null || data.size() == 0) {
                throw new IOException("No image in the response.");
            }
            JsonObject first = data.get(0).getAsJsonObject();
            if (first.has("b64_json") && first.get("b64_json").isJsonPrimitive()) {
                return java.util.Base64.getDecoder().decode(first.get("b64_json").getAsString());
            }
            throw new IOException("The image response had no b64_json field.");
        } catch (RuntimeException e) {
            throw new IOException("Could not read the image response: " + e.getMessage());
        }
    }

    // ------------------------------------------------------ account (pollen)

    public record Balance(double pollen, double questPollen, double paidPollen) {}

    /**
     * Cost of the newest billed request on this key, in USD — at $1 ≈ 1 Pollen
     * that number IS the Pollen cost. Called right after a request, so the
     * newest record is ours. Best-effort: callers use {@link #costLine} and
     * simply drop the detail when the key can't read its own usage.
     */
    public static double lastCostUsd(String apiKey) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN + "/account/key/usage?format=json&limit=1"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Usage lookup failed: " + errorMessage(res.body(), res.statusCode()));
        }
        return parseLastCost(res.body());
    }

    public static double parseLastCost(String json) throws IOException {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonArray usage = o.getAsJsonArray("usage");
            if (usage == null || usage.size() == 0) {
                return 0;
            }
            JsonObject first = usage.get(0).getAsJsonObject();
            return first.has("cost_usd") && first.get("cost_usd").isJsonPrimitive()
                    ? first.get("cost_usd").getAsDouble()
                    : 0;
        } catch (RuntimeException e) {
            throw new IOException("Could not read the usage history: " + e.getMessage());
        }
    }

    /** " · ~0.00008614 Pollen", or "" when the key can't read its own usage. */
    public static String costLine(String apiKey) {
        try {
            return " · " + formatPollen(lastCostUsd(apiKey));
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatPollen(double usd) {
        String s = String.format(java.util.Locale.ROOT, "%.8f", usd);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return "~" + s + " Pollen";
    }

    /** GET /account/balance — Pollen visible to this key (budgeted keys see their remaining budget). */
    public static Balance balance(String apiKey) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN + "/account/balance"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Balance lookup failed: " + errorMessage(res.body(), res.statusCode()));
        }
        return parseBalance(res.body());
    }

    public static Balance parseBalance(String json) throws IOException {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            double balance = o.has("balance") ? o.get("balance").getAsDouble() : 0;
            double tier = 0;
            double paid = 0;
            if (o.has("accountBalance") && o.get("accountBalance").isJsonObject()) {
                JsonObject ab = o.getAsJsonObject("accountBalance");
                if (ab.has("tier")) {
                    tier = ab.get("tier").getAsDouble();
                }
                if (ab.has("paid")) {
                    paid = ab.get("paid").getAsDouble();
                }
            }
            return new Balance(balance, tier, paid);
        } catch (RuntimeException e) {
            throw new IOException("Could not read the balance: " + e.getMessage());
        }
    }

    public static List<ModelInfo> listModels(String apiKey) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(GEN + "/text/models"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Model list failed: " + errorMessage(res.body(), res.statusCode()));
        }
        return parseModels(res.body());
    }
}
