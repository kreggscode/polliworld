package com.kreggscode.polliworld.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Pollinations BYOP device flow against enter.pollinations.ai:
 * POST /api/device/code to start, then poll POST /api/device/token until the
 * visitor approves in their browser. Parsing is separated from HTTP so it can
 * be unit-tested.
 */
public final class DeviceFlow {

    public static final String START_URL = "https://enter.pollinations.ai/api/device/code";
    public static final String TOKEN_URL = "https://enter.pollinations.ai/api/device/token";
    public static final String CLIENT_ID = "pollinations-web-app";

    public record Start(String deviceCode, String userCode, String verificationUriComplete, int expiresInSeconds, int intervalSeconds) {}

    /** Still waiting for the visitor to approve — retry after intervalSeconds. */
    public static class PendingException extends Exception {
        public PendingException(String message) {
            super(message);
        }
    }

    /** The visitor denied the request, or the code expired / was already used. */
    public static class FailedException extends Exception {
        public FailedException(String message) {
            super(message);
        }
    }

    private DeviceFlow() {}

    public static String startJson(String scope) {
        JsonObject o = new JsonObject();
        o.addProperty("client_id", CLIENT_ID);
        o.addProperty("scope", scope);
        return o.toString();
    }

    public static String tokenJson(String deviceCode) {
        JsonObject o = new JsonObject();
        o.addProperty("client_id", CLIENT_ID);
        o.addProperty("device_code", deviceCode);
        o.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        return o.toString();
    }

    public static Start parseStart(String json) throws FailedException {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String deviceCode = o.get("device_code").getAsString();
            String userCode = o.get("user_code").getAsString();
            String uri = o.has("verification_uri_complete")
                    ? o.get("verification_uri_complete").getAsString()
                    : o.get("verification_uri").getAsString() + "?user_code=" + userCode;
            int expires = o.has("expires_in") ? o.get("expires_in").getAsInt() : 1800;
            int interval = o.has("interval") ? o.get("interval").getAsInt() : 5;
            return new Start(deviceCode, userCode, uri, expires, interval);
        } catch (RuntimeException e) {
            throw new FailedException("Could not read the device-code response: " + e.getMessage());
        }
    }

    /**
     * Parses a token-poll response. Returns the visitor's API key when approved,
     * throws PendingException while waiting, FailedException on denial/expiry.
     */
    public static String parseToken(String json) throws PendingException, FailedException {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new FailedException("Could not read the token response.");
        }
        for (String field : new String[] {"access_token", "token", "key"}) {
            JsonElement el = o.get(field);
            if (el != null && el.isJsonPrimitive() && !el.getAsString().isBlank()) {
                return el.getAsString();
            }
        }
        String error = o.has("error") ? o.get("error").getAsString() : "";
        if (error.equals("authorization_pending") || error.equals("slow_down")) {
            throw new PendingException(error);
        }
        throw new FailedException(error.isBlank() ? "Sign-in was not approved." : "Sign-in failed: " + error);
    }
}
