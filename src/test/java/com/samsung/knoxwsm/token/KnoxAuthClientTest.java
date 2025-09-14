package com.samsung.knoxwsm.token;

/*-
 * #%L
 * knox-token-utility
 * %%
 * Copyright (C) 2025 Samsung
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link KnoxAuthClient} focusing on Knox Guard & token lifecycle (KME removed).
 */
public class KnoxAuthClientTest {
    private MockWebServer mockWebServer;
    private KnoxAuthClient authClient;
    private ObjectMapper mapper;

    private static final String DUMMY_PUBLIC_KEY_BASE64 = Base64.getEncoder().encodeToString("PUBLICKEY".getBytes());
    private static final String DUMMY_ACCESS_TOKEN = "dummy.access.token";
    private static final String DUMMY_REFRESH_TOKEN = "dummy.refresh.token";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        // Instantiate client pointing to mock server base (already includes version path)
        String base = mockWebServer.url("/kcs/v1").toString();
        authClient = new KnoxAuthClient(base);
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testRequestAccessTokenSuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{" +
                "\"accessToken\":\"" + DUMMY_ACCESS_TOKEN + "\"," +
                "\"refreshToken\":\"" + DUMMY_REFRESH_TOKEN + "\"," +
                "\"expiresIn\":1800}"));

        Map<String, Object> result = authClient.requestAccessToken(DUMMY_PUBLIC_KEY_BASE64, "jwt-value", 30);
        assertEquals(DUMMY_ACCESS_TOKEN, result.get("accessToken"));
        assertEquals(DUMMY_REFRESH_TOKEN, result.get("refreshToken"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/kcs/v1/ses/token", req.getPath());
        assertEquals("jwt-value", req.getHeader("X-SES-JWT"));
    }

    @Test
    void testRefreshAccessTokenSuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{" +
                "\"accessToken\":\"" + DUMMY_ACCESS_TOKEN + "2\"," +
                "\"refreshToken\":\"" + DUMMY_REFRESH_TOKEN + "2\"," +
                "\"expiresIn\":1800}"));

        Map<String, Object> result = authClient.refreshAccessToken(DUMMY_PUBLIC_KEY_BASE64, DUMMY_REFRESH_TOKEN, 30);
        assertEquals(DUMMY_ACCESS_TOKEN + "2", result.get("accessToken"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/kcs/v1/ses/token/refresh", req.getPath());
    }

    @Test
    void testValidateAccessTokenSuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"valid\":true}"));

        Map<String, Object> result = authClient.validateAccessToken(DUMMY_ACCESS_TOKEN);
        assertEquals(Boolean.TRUE, result.get("valid"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/kcs/v1/ses/token/validate", req.getPath());
        assertEquals("Bearer " + DUMMY_ACCESS_TOKEN, req.getHeader("Authorization"));
    }

    @Test
    void testEnrollDeviceInKnoxGuardSuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"enrolled\":true}"));

        Map<String, Object> result = authClient.enrollDeviceInKnoxGuard(DUMMY_ACCESS_TOKEN, "359881234567890", "client-123");
        assertEquals(Boolean.TRUE, result.get("enrolled"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/kcs/v1/kguard/devices", req.getPath());
    }


    @Test
    void testValidityRangeValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            authClient.requestAccessToken(DUMMY_PUBLIC_KEY_BASE64, "jwt", 10)
        );
        assertThrows(IllegalArgumentException.class, () ->
            authClient.requestAccessToken(DUMMY_PUBLIC_KEY_BASE64, "jwt", 120)
        );
    }

    @Test
    void testRequestAccessTokenUnauthorized() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"INVALID_JWT\"}"));
        KnoxApiException ex = assertThrows(KnoxApiException.class, () ->
            authClient.requestAccessToken(DUMMY_PUBLIC_KEY_BASE64, "bad-jwt", 30)
        );
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    void testEnrollDeviceBadRequest() throws Exception {
        // first token success
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"accessToken\":\"t\",\"refreshToken\":\"r\",\"expiresIn\":1800}"));
        Map<String, Object> token = authClient.requestAccessToken(DUMMY_PUBLIC_KEY_BASE64, "jwt-value", 30);
        String at = (String) token.get("accessToken");
        // enrollment failure
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"INVALID_DEVICE\"}"));
        KnoxApiException ex = assertThrows(KnoxApiException.class, () ->
            authClient.enrollDeviceInKnoxGuard(at, "BADIMEI", "client-123")
        );
        assertEquals(400, ex.getStatusCode());
    }
}
