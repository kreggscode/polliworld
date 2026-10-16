package com.kreggscode.polliworld.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeviceFlowTest {

    @Test
    void parsesTheStartResponseTheServiceActuallyReturns() throws Exception {
        // Shape verified live against enter.pollinations.ai/api/device/code.
        String json = """
                {"device_code":"abc123","user_code":"CXE7BXDK","verification_uri":"https://enter.pollinations.ai/device","verification_uri_complete":"https://enter.pollinations.ai/device?user_code=CXE7BXDK","expires_in":1800,"interval":5}""";
        DeviceFlow.Start start = DeviceFlow.parseStart(json);
        assertEquals("abc123", start.deviceCode());
        assertEquals("CXE7BXDK", start.userCode());
        assertEquals("https://enter.pollinations.ai/device?user_code=CXE7BXDK", start.verificationUriComplete());
        assertEquals(1800, start.expiresInSeconds());
        assertEquals(5, start.intervalSeconds());
    }

    @Test
    void buildsCompleteUriWhenOnlyTheBaseIsGiven() throws Exception {
        String json = """
                {"device_code":"d","user_code":"U1","verification_uri":"https://enter.pollinations.ai/device"}""";
        assertEquals("https://enter.pollinations.ai/device?user_code=U1",
                DeviceFlow.parseStart(json).verificationUriComplete());
    }

    @Test
    void pendingWhileTheVisitorHasNotApprovedYet() {
        // Shape verified live: the service answers 400 {"error":"authorization_pending"}.
        assertThrows(DeviceFlow.PendingException.class,
                () -> DeviceFlow.parseToken("{\"error\":\"authorization_pending\"}"));
        assertThrows(DeviceFlow.PendingException.class,
                () -> DeviceFlow.parseToken("{\"error\":\"slow_down\"}"));
    }

    @Test
    void approvedResponseYieldsTheKey() throws Exception {
        assertEquals("pk_visitor_123", DeviceFlow.parseToken(
                "{\"access_token\":\"pk_visitor_123\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        assertEquals("pk_x", DeviceFlow.parseToken("{\"token\":\"pk_x\"}"));
        assertEquals("pk_y", DeviceFlow.parseToken("{\"key\":\"pk_y\"}"));
    }

    @Test
    void denialAndGarbageFailCleanly() {
        assertThrows(DeviceFlow.FailedException.class,
                () -> DeviceFlow.parseToken("{\"error\":\"access_denied\"}"));
        assertThrows(DeviceFlow.FailedException.class,
                () -> DeviceFlow.parseToken("{\"error\":\"expired_token\"}"));
        assertThrows(DeviceFlow.FailedException.class,
                () -> DeviceFlow.parseToken("not json at all"));
    }

    @Test
    void requestBodiesCarryTheRightFields() {
        assertTrue(DeviceFlow.startJson("api:read api:write").contains("client_id"));
        assertTrue(DeviceFlow.startJson("api:read api:write").contains("api:read api:write"));
        String token = DeviceFlow.tokenJson("dev123");
        assertTrue(token.contains("dev123"));
        assertTrue(token.contains("urn:ietf:params:oauth:grant-type:device_code"));
    }
}
