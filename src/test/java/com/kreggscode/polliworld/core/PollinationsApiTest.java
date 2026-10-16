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

    @Test
    void decodesTheImageResponse() throws Exception {
        // Shape verified live against POST /v1/images/generations.
        String payload = java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});
        byte[] bytes = PollinationsApi.parseImageResponse("{\"data\":[{\"b64_json\":\"" + payload + "\"}]}");
        assertArrayEquals(new byte[] {1, 2, 3, 4}, bytes);
    }

    @Test
    void imageResponseWithoutDataIsAnError() {
        assertThrows(IOException.class, () -> PollinationsApi.parseImageResponse("{\"data\":[]}"));
        assertThrows(IOException.class, () -> PollinationsApi.parseImageResponse("garbage"));
    }

    @Test
    void parsesTheNewestUsageCost() throws Exception {
        // Shape verified live against gen.pollinations.ai/account/key/usage.
        String json = """
                {"usage":[{"timestamp":"2026-10-16 12:00:01","type":"generate.text",
                  "model":"openai/gpt-5.4-nano","cost_usd":0.00008614}],"count":1}""";
        assertEquals(0.00008614, PollinationsApi.parseLastCost(json), 1e-12);
        assertEquals(0, PollinationsApi.parseLastCost("{\"usage\":[],\"count\":0}"), 1e-12);
        assertThrows(IOException.class, () -> PollinationsApi.parseLastCost("garbage"));
    }

    @Test
    void formatsPollenCostsForChat() {
        assertEquals("~0.00008614 Pollen", PollinationsApi.formatPollen(0.00008614));
        assertEquals("~0.5 Pollen", PollinationsApi.formatPollen(0.5));
    }

    @Test
    void parsesBalanceWithAndWithoutAccountBreakdown() throws Exception {
        // Shape verified live against gen.pollinations.ai/account/balance.
        PollinationsApi.Balance b = PollinationsApi.parseBalance(
                "{\"balance\":12.5,\"accountBalance\":{\"total\":12.5,\"tier\":8,\"paid\":4.5}}");
        assertEquals(12.5, b.pollen(), 1e-9);
        assertEquals(8, b.questPollen(), 1e-9);
        assertEquals(4.5, b.paidPollen(), 1e-9);
        PollinationsApi.Balance budgeted = PollinationsApi.parseBalance("{\"balance\":3.25}");
        assertEquals(3.25, budgeted.pollen(), 1e-9);
        assertEquals(0, budgeted.questPollen(), 1e-9);
        assertThrows(IOException.class, () -> PollinationsApi.parseBalance("garbage"));
    }
}
