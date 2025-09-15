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
    // Default production endpoint (global fallback if region not specified)
    private static final String DEFAULT_API_BASE_URL = "https://us-api.samsungknox.com/kcs/v1";
    // Default Guard functional base (Upload / operational endpoints) - spec currently at v1.1
    private static final String DEFAULT_GUARD_FUNCTION_BASE = "https://us-kcs-api.samsungknox.com/kcs/v1.1/kg";
    // Region mapping (minimal) - extend as needed
    private static String resolveRegionalBaseUrl() {
        String explicit = System.getenv("KNOX_API_BASE_URL");
        if (explicit != null && !explicit.isBlank()) return explicit;
        String region = System.getenv().getOrDefault("KNOX_REGION", "").toLowerCase();
        // Known region codes: us, eu, ap (example). Default to us if unspecified/unknown.
        return switch (region) {
            case "eu" -> "https://eu-api.samsungknox.com/kcs/v1";
            case "ap" -> "https://ap-api.samsungknox.com/kcs/v1";
            case "us", "" -> "https://us-api.samsungknox.com/kcs/v1";
            default -> "https://" + region + "-api.samsungknox.com/kcs/v1"; // last attempt (custom region code)
        };
    }
    // Resolve Guard functional base (separate host pattern us-kcs-api vs us-api). Allow override via KNOX_GUARD_FUNCTION_BASE_URL.
    private static String resolveGuardFunctionBaseUrl() {
        String explicit = System.getenv("KNOX_GUARD_FUNCTION_BASE_URL");
        if (explicit != null && !explicit.isBlank()) return explicit.replaceAll("/+$$", "");
        String region = System.getenv().getOrDefault("KNOX_REGION", "").toLowerCase();
        return switch (region) {
            case "eu" -> "https://eu-kcs-api.samsungknox.com/kcs/v1.1/kg";
            case "ap" -> "https://ap-kcs-api.samsungknox.com/kcs/v1.1/kg";
            case "us", "" -> DEFAULT_GUARD_FUNCTION_BASE;
            default -> "https://" + region + "-kcs-api.samsungknox.com/kcs/v1.1/kg";
        };
    }
    // Desired API version (header + path injection only if not already present in base URL)
    protected static final String API_VERSION = System.getenv().getOrDefault("KNOX_API_VERSION", "v1");
    private final String apiBaseUrl;
    private final String guardFunctionBaseUrl; // for /devices/uploads etc.
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
        this(resolveRegionalBaseUrl(), resolveGuardFunctionBaseUrl());
    }

    public KnoxAuthClient(String apiBaseUrl) {
        this(apiBaseUrl, resolveGuardFunctionBaseUrl());
    }

    public KnoxAuthClient(String apiBaseUrl, String guardFunctionBaseUrl) {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.mapper = new ObjectMapper();
        // normalize remove trailing slash
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$$", "");
        this.guardFunctionBaseUrl = guardFunctionBaseUrl.replaceAll("/+$$", "");
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

    /**
     * Upload a batch of devices (Knox Guard Upload API). Assumes caller already possesses an access token.
     * @param accessToken x-knox-apitoken header value
     * @param uploadPayload JSON string containing deviceList and optional flags (autoAccept, autoLock, etc.)
     * @return parsed response map
     */
    public Map<String,Object> uploadDevices(String accessToken, String uploadPayload) throws IOException {
        if (uploadPayload == null || uploadPayload.isBlank()) throw new IllegalArgumentException("uploadPayload required");
        String url = guardFunctionBaseUrl + "/devices/uploads";
        Request request = new Request.Builder()
            .url(url)
            .header("x-knox-apitoken", accessToken)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .post(RequestBody.create(uploadPayload, JSON))
            .build();
        log.debug("Uploading devices payloadSize={}", uploadPayload.length());
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Device upload failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "upload devices", null);
            }
            return mapper.readValue(safeBodyString(response), Map.class);
        }
    }

    /** List device uploads (last 1000). */
    public Map<String,Object> listDeviceUploads(String accessToken) throws IOException {
        String url = guardFunctionBaseUrl + "/devices/uploads";
        Request request = new Request.Builder()
            .url(url)
            .header("x-knox-apitoken", accessToken)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .get().build();
        log.debug("Listing device uploads");
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("List uploads failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "list device uploads", null);
            }
            return mapper.readValue(safeBodyString(response), Map.class);
        }
    }

    /** Get upload details by uploadId. */
    public Map<String,Object> getUploadById(String accessToken, String uploadId) throws IOException {
        if (uploadId == null || uploadId.isBlank()) throw new IllegalArgumentException("uploadId required");
        String url = guardFunctionBaseUrl + "/devices/uploads/" + uploadId;
        Request request = new Request.Builder()
            .url(url)
            .header("x-knox-apitoken", accessToken)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .get().build();
        log.debug("Fetching uploadId={}", uploadId);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Get upload failed status={} id={} url={}", response.code(), uploadId, request.url());
                throw buildApiException(response, "get upload details", null);
            }
            return mapper.readValue(safeBodyString(response), Map.class);
        }
    }

    /**
     * Unlock a previously enrolled device. Endpoint path is configurable via env var KNOX_GUARD_UNLOCK_PATH (default /kguard/devices/unlock).
     * NOTE: Endpoint pattern assumed; adjust if server returns 404 and update KNOX_GUARD_UNLOCK_PATH accordingly.
     * @param accessToken Access token (Bearer)
     * @param deviceImei IMEI (15 digits)
     * @return response map
     */
    public Map<String,Object> unlockDevice(String accessToken, String deviceImei) throws IOException {
        if (deviceImei == null || !deviceImei.matches("\\d{14,16}")) {
            throw new IllegalArgumentException("deviceImei must be 15-digit numeric (allowing temporary 14-16 for testing)");
        }
        String path = System.getenv().getOrDefault("KNOX_GUARD_UNLOCK_PATH", "/kguard/devices/unlock");
        String requestBody = mapper.writeValueAsString(Map.of(
            "deviceId", deviceImei,
            "action", "unlock"
        ));
        Request request = new Request.Builder()
            .url(buildUrl(path))
            .header("Authorization", "Bearer " + accessToken)
            .header("X-KNOX-API-VERSION", API_VERSION)
            .post(RequestBody.create(requestBody, JSON))
            .build();
        log.debug("Unlocking device imei={}", deviceImei);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Unlock device failed status={} url={}", response.code(), request.url());
                throw buildApiException(response, "unlock device", null);
            }
            return mapper.readValue(safeBodyString(response), Map.class);
        }
    }

    /**
     * Convenience helper: enroll then immediately unlock the device, returning a composite result map.
     * Keys: enrollment, unlock.
     */
    public Map<String,Object> enrollAndUnlock(String accessToken, String deviceImei, String clientId) throws IOException {
        Map<String,Object> enrollment = enrollDeviceInKnoxGuard(accessToken, deviceImei, clientId);
        Map<String,Object> unlock = unlockDevice(accessToken, deviceImei);
        java.util.HashMap<String,Object> combined = new java.util.HashMap<>();
        combined.put("enrollment", enrollment);
        combined.put("unlock", unlock);
        return combined;
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
