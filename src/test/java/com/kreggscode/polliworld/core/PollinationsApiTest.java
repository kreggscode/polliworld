package com.kreggscode.polliworld.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PollinationsApiTest {

    @Test
    void parsesCompletionWithUsage() throws Exception {
        // Shape verified live against gen.pollinations.ai/v1/chat/completions.
        String json = """
                {"choices":[{"finish_reason":"stop","index":0,"message":{"content":"ok","role":"assistant"}}],
                 "usage":{"completion_tokens":4,"prompt_tokens":9}}""";
        PollinationsApi.Completion c = PollinationsApi.parseCompletion(json);
        assertEquals("ok", c.content());
        assertEquals(9, c.inTokens());
        assertEquals(4, c.outTokens());
    }

    @Test
    void parsesCompletionWithoutUsage() throws Exception {
        PollinationsApi.Completion c = PollinationsApi.parseCompletion(
                "{\"choices\":[{\"message\":{\"content\":\"hello there\"}}]}");
        assertEquals("hello there", c.content());
        assertEquals(0, c.inTokens());
        assertEquals(0, c.outTokens());
    }

    @Test
    void emptyContentIsAnErrorNotSilence() {
        assertThrows(IOException.class,
                () -> PollinationsApi.parseCompletion("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        assertThrows(IOException.class, () -> PollinationsApi.parseCompletion("garbage"));
    }

    @Test
    void parsesTheLiveModelListShape() throws Exception {
        // Shape verified live against gen.pollinations.ai/text/models.
        String json = """
                [{"name":"openai/gpt-5.4-nano","description":"fast","paid_only":false},
                 {"name":"google/gemini-4-argon","paid_only":true},
                 {"description":"no name, skipped"}]""";
        List<PollinationsApi.ModelInfo> models = PollinationsApi.parseModels(json);
        assertEquals(2, models.size());
        assertEquals("openai/gpt-5.4-nano", models.get(0).name());
        assertFalse(models.get(0).paidOnly());
        assertEquals("fast", models.get(0).description());
        assertTrue(models.get(1).paidOnly());
        assertEquals("", models.get(1).description());
    }

    @Test
    void extractsHumanErrorMessages() {
        // Shape verified live: gen's 400 body nests the message under error.
        String nested = """
                {"success":false,"error":{"message":"Respan only accepts noul questions","code":"BAD_REQUEST"}}""";
        assertEquals("Respan only accepts noul questions", PollinationsApi.errorMessage(nested, 400));
        assertEquals("rate limited", PollinationsApi.errorMessage("{\"error\":\"rate limited\"}", 429));
        assertEquals("HTTP 500", PollinationsApi.errorMessage("oops", 500));
    }
}
