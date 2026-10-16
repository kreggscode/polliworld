package com.kreggscode.polliworld.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kreggscode.polliworld.core.MemoryStore.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestBuilderTest {

    @Test
    void buildsSystemHistoryAndUserMessageInOrder() {
        List<Msg> history = List.of(
                new Msg(1, "user", "first question"),
                new Msg(2, "assistant", "first answer"));
        JsonObject body = JsonParser.parseString(
                RequestBuilder.chatJson("openai/gpt-5.4-nano", "You are Bjorn.", history, "new question"))
                .getAsJsonObject();
        assertEquals("openai/gpt-5.4-nano", body.get("model").getAsString());
        JsonArray messages = body.getAsJsonArray("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("You are Bjorn.", messages.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("first question", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("new question", messages.get(3).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void skipsBlankSystemPrompt() {
        JsonObject body = JsonParser.parseString(RequestBuilder.chatJson("m", "", List.of(), "hi")).getAsJsonObject();
        assertEquals(1, body.getAsJsonArray("messages").size());
        assertEquals("user", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
    }

    @Test
    void coercesUnknownRolesToUser() {
        List<Msg> history = List.of(new Msg(1, "(ask)", "old question"));
        JsonObject body = JsonParser.parseString(RequestBuilder.chatJson("m", null, history, "now")).getAsJsonObject();
        assertEquals("user", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
    }
}
