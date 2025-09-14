/*
 * Copyright 2025 Samsung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
// Removed unused Headers import

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for interacting with the Samsung Knox Cloud Authentication API.
 */
public class KnoxAuthClient {
    // Default production endpoint (may already contain the version segment)
    private static final String DEFAULT_API_BASE_URL = "https://api.samsungknox.com/kcs/v1";
    // Desired API version (header + path injection only if not already present in base URL)
    protected static final String API_VERSION = System.getenv().getOrDefault("KNOX_API_VERSION", "v1");
    private final String apiBaseUrl;
    // Build full URL ensuring we don't duplicate version segment when apiBaseUrl already ends with /vX or /vX.Y
    private String buildUrl(String relativePath) {
        String base = apiBaseUrl;
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        if (base.matches(".*/v\\d+(\\.\\d+)?$")) {
            return base + path;
        }
        return base.replaceAll("/+$$", "") + "/" + API_VERSION + path;
    }
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Logger log = LoggerFactory.getLogger(KnoxAuthClient.class);
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public KnoxAuthClient() {
        this(System.getenv().getOrDefault("KNOX_API_BASE_URL", DEFAULT_API_BASE_URL));
    }

    public KnoxAuthClient(String apiBaseUrl) {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.mapper = new ObjectMapper();
        // normalize remove trailing slash
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$$", "");
    }

    /**
     * Request an access token using a client identifier JWT.
     *
     * @param publicKey Base64-encoded public key
     * @param clientIdentifierJwt JWT token containing client identity claims
     * @param validityMinutes Validity period for the access token in minutes (15-60)
     * @return Map containing the access token response
     * @throws IOException if the request fails
     */
    public Map<String, Object> requestAccessToken(
            String publicKey, 
            String clientIdentifierJwt,
            int validityMinutes
    ) throws IOException {
        if (validityMinutes < 15 || validityMinutes > 60) {
            throw new IllegalArgumentException("Validity period must be between 15 and 60 minutes");
        }

        String requestBody = mapper.writeValueAsString(Map.of(
            "base64EncodedStringPublicKey", publicKey,
            "validityForAccessTokenInMinutes", validityMinutes
        ));

        Request request = new Request.Builder()
            .url(buildUrl("/ses/token"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-SES-JWT", clientIdentifierJwt)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .post(RequestBody.create(requestBody, JSON))
            .build();

        log.debug("Requesting access token validityMinutes={}", validityMinutes);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Access token request failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "request access token",
                        "401".equals(String.valueOf(response.code())) ?
                            "Verify the JWT signature, client ID, and that the public key is registered in the Knox portal." : null);
            }
            String responseBody = safeBodyString(response);
            log.debug("Access token response length={}", responseBody.length());
            return mapper.readValue(responseBody, Map.class);
        }
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * @param publicKey Base64-encoded public key
     * @param refreshToken The refresh token from a previous token response
     * @param validityMinutes Validity period for the new access token in minutes (15-60)
     * @return Map containing the new access token response
     * @throws IOException if the request fails
     */
    public Map<String, Object> refreshAccessToken(
            String publicKey,
            String refreshToken,
            int validityMinutes
    ) throws IOException {
        if (validityMinutes < 15 || validityMinutes > 60) {
            throw new IllegalArgumentException("Validity period must be between 15 and 60 minutes");
        }

        String requestBody = mapper.writeValueAsString(Map.of(
            "base64EncodedStringPublicKey", publicKey,
            "refreshToken", refreshToken,
            "validityForAccessTokenInMinutes", validityMinutes
        ));

        Request request = new Request.Builder()
            .url(buildUrl("/ses/token/refresh"))
            .header("X-KNOX-API-VERSION", API_VERSION)
            .post(RequestBody.create(requestBody, JSON))
            .build();

        log.debug("Refreshing access token validityMinutes={}", validityMinutes);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Refresh access token failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "refresh access token", null);
            }
            String responseBody = safeBodyString(response);
            return mapper.readValue(responseBody, Map.class);
        }
    }

    /**
     * Validate an access token.
     *
     * @param accessToken The access token to validate
     * @return Map containing the validation response
     * @throws IOException if the request fails
     */
    public Map<String, Object> validateAccessToken(String accessToken) throws IOException {
        Request request = new Request.Builder()
            .url(buildUrl("/ses/token/validate"))
            .header("Authorization", "Bearer " + accessToken)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .get()
            .build();

        log.debug("Validating access token");
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Validate access token failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "validate access token", null);
            }
            String responseBody = safeBodyString(response);
            return mapper.readValue(responseBody, Map.class);
        }
    }

    /**
     * Enroll a device in Knox Guard.
     *
     * @param accessToken The access token for authentication
     * @param deviceImei The device IMEI
     * @param clientId The Knox Guard client ID
     * @return Map containing the enrollment response
     * @throws IOException if the request fails
     */
    public Map<String, Object> enrollDeviceInKnoxGuard(
            String accessToken,
            String deviceImei,
            String clientId
    ) throws IOException {
        String requestBody = mapper.writeValueAsString(Map.of(
            "deviceId", deviceImei,
            "clientId", clientId,
            "platform", "android"
        ));

        Request request = new Request.Builder()
            .url(buildUrl("/kguard/devices"))
            .header("Authorization", "Bearer " + accessToken)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .post(RequestBody.create(requestBody, JSON))
            .build();

        log.debug("Enrolling device clientId={} imei={}", clientId, deviceImei);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Device enrollment failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "enroll device in Knox Guard",
                        response.code() == 400 ? "Check IMEI format and clientId correctness." : null);
            }
            String responseBody = safeBodyString(response);
            return mapper.readValue(responseBody, Map.class);
        }
    }

    // Helper to read body safely without NPE.
    private String safeBodyString(Response response) throws IOException {
        if (response == null) {
            return "";
        }
        okhttp3.ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private KnoxApiException buildApiException(Response response, String action, String explicitSuggestion) throws IOException {
        int code = response.code();
        String body = safeBodyString(response);
        String suggestion = explicitSuggestion;
        if (suggestion == null) {
            switch (code) {
                case 400 -> suggestion = "Bad request while attempting to " + action + ". Verify required parameters and JSON schema.";
                case 401 -> suggestion = "Unauthorized while attempting to " + action + ". Confirm JWT validity, time skew, and public key registration.";
                case 403 -> suggestion = "Forbidden while attempting to " + action + ". Check that the account/service has Knox Guard permissions.";
                case 404 -> suggestion = "Endpoint not found; confirm base URL (" + apiBaseUrl + ") is correct.";
                default -> {
                    if (code >= 500) suggestion = "Server error (" + code + ") while attempting to " + action + ". Retry later or contact Samsung support.";
                    else suggestion = "HTTP " + code + " returned while attempting to " + action + ".";
                }
            }
        }
        return new KnoxApiException(code, body, suggestion);
    }

}
