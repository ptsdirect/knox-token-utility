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

import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.samsung.knoxwsm.token.KnoxTokenUtility;
import com.samsung.knoxwsm.token.TokenClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KnoxAPITest {
    private MockWebServer mockWebServer;
    private OkHttpClient client;
    private String privateKeyPath;
    private String publicKeyPath;
    private String clientId;
    private ObjectMapper mapper;
    private String mockApiUrl;

    @BeforeEach
    public void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockApiUrl = mockWebServer.url("/kcs/v1/ses/token").toString();
        
        client = new OkHttpClient.Builder().build();
        mapper = new ObjectMapper();
        clientId = "test-client-" + System.currentTimeMillis();

        // Create a temporary test key pair
        Path tempDir = Files.createTempDirectory("knox-test-");
        privateKeyPath = tempDir.resolve("private_key.pem").toString();
        publicKeyPath = tempDir.resolve("public_key.pem").toString();

        // Use TokenClient to generate test key pair
        TokenClient.generateKeyPair(
            Paths.get(privateKeyPath),
            Paths.get(publicKeyPath)
        );
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockWebServer.shutdown();
        Files.walk(Paths.get(privateKeyPath).getParent())
            .sorted((a, b) -> -a.compareTo(b))
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }

    @Test
    public void testKnoxTokenAPI() throws Exception {
        // Read the private key file
        Path keyPath = Paths.get(privateKeyPath);
        assertTrue(Files.exists(keyPath), "Private key file must exist");

        // Generate JWT token
        String jwt = KnoxTokenUtility.generateSignedClientIdentifierJWT(
            new FileInputStream(privateKeyPath),
            clientId,
            null
        );

        // Read public key for request
        String publicKeyContent = Files.readString(Paths.get(publicKeyPath));
        String publicKeyBase64 = Base64.getEncoder().encodeToString(
            publicKeyContent.getBytes()
        );

        // Prepare mock response
        Map<String, Object> mockResponse = Map.of(
            "accessToken", "mock.access.token",
            "refreshToken", "mock.refresh.token",
            "expiresIn", 1800
        );

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mapper.writeValueAsString(mockResponse)));

        // Create request body
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String requestBody = String.format("""
            {
                "base64EncodedStringPublicKey": "%s",
                "clientIdentifierJwt": "%s",
                "validityForAccessTokenInMinutes": 30
            }""", 
            publicKeyBase64, 
            jwt
        );

        // Build request
        Request request = new Request.Builder()
            .url(mockApiUrl)
            .post(RequestBody.create(requestBody, JSON))
            .build();

        // Execute request
        try (Response response = client.newCall(request).execute()) {
            assertTrue(response.isSuccessful(), "API call should be successful");
            assertNotNull(response.body(), "Response body should not be null");
            
            String responseBody = response.body().string();
            Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);
            
            assertNotNull(responseMap.get("accessToken"), "Response should contain access token");
            assertNotNull(responseMap.get("refreshToken"), "Response should contain refresh token");
            assertEquals(1800, responseMap.get("expiresIn"), "Response should contain correct expiresIn value");
        }

        // Verify request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/kcs/v1/ses/token", recordedRequest.getPath());
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"));

        // Verify request body
        Map<String, Object> requestMap = mapper.readValue(recordedRequest.getBody().readUtf8(), Map.class);
        assertEquals(publicKeyBase64, requestMap.get("base64EncodedStringPublicKey"));
        assertEquals(jwt, requestMap.get("clientIdentifierJwt"));
        assertEquals(30, requestMap.get("validityForAccessTokenInMinutes"));
    }
}
