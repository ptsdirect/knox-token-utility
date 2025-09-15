/*
 * Knox Guard Client Extensions
 * Added device upload & query feature.
 */
package com.samsung.knoxwsm.token;

/*-
 * #%L
 * Knox Guard Token Utility
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Knox Guard domain client (device upload & related queries) leveraging existing region logic.
 * This intentionally does NOT duplicate auth flows; caller must supply a valid access token.
 */
public class KnoxGuardClient {
    private static final Logger log = LoggerFactory.getLogger(KnoxGuardClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final ObjectMapper mapper;
    // Expected to be like https://us-api.samsungknox.com/kcs/v1 OR v1.1
    private final String base;

    public KnoxGuardClient() {
        this(resolveRegionalBaseUrl());
    }

    public KnoxGuardClient(String baseUrl) {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
        this.base = baseUrl.replaceAll("/+$$", "");
    }

    private static String resolveRegionalBaseUrl() {
        String explicit = System.getenv("KNOX_API_BASE_URL");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String region = System.getenv().getOrDefault("KNOX_REGION", "us").toLowerCase();
        switch (region) {
            case "eu":
                return "https://eu-api.samsungknox.com/kcs/v1";
            case "ap":
                return "https://ap-api.samsungknox.com/kcs/v1";
            case "us":
            default:
                return "https://us-api.samsungknox.com/kcs/v1";
        }
    }

    private String buildUrl(String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        // base already includes version selection outside (we keep same heuristic as auth)
        return base + path;
    }

    private static String apiVersionHeader() {
        return System.getenv().getOrDefault("KNOX_API_VERSION", "v1");
    }

    /** Upload a batch of devices (synchronous accepted response referencing uploadID). */
    public Map<String, Object> uploadDevices(
            String accessToken,
            List<Map<String, Object>> deviceList,
            Map<String, Object> policyFlags) throws IOException {
        if (deviceList == null || deviceList.isEmpty()) {
            throw new IllegalArgumentException("deviceList required");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceList", deviceList);
        if (policyFlags != null) {
            payload.putAll(policyFlags);
        }
        String body = mapper.writeValueAsString(payload);
        Request req = new Request.Builder()
                .url(buildUrl("/devices/uploads"))
                .header("x-knox-apitoken", accessToken)
                .header("X-KNOX-API-VERSION", apiVersionHeader())
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String raw = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                log.warn("Upload devices failed status={} bodySize={}", resp.code(), raw.length());
                throw new KnoxApiException(resp.code(), raw, "Upload devices failed");
            }
            return mapper.readValue(raw, new TypeReference<Map<String, Object>>() { });
        }
    }

    /** List recent uploads (last 1000). */
    public Map<String, Object> listUploads(String accessToken) throws IOException {
        HttpUrl url = HttpUrl.parse(buildUrl("/devices/uploads"));
        Request req = new Request.Builder()
                .url(url)
                .header("x-knox-apitoken", accessToken)
                .header("X-KNOX-API-VERSION", apiVersionHeader())
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String raw = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                log.warn("List uploads failed status={} bodySize={}", resp.code(), raw.length());
                throw new KnoxApiException(resp.code(), raw, "List uploads failed");
            }
            return mapper.readValue(raw, new TypeReference<Map<String, Object>>() { });
        }
    }

    /** Get upload detail by uploadId (device statuses). */
    public Map<String, Object> getUploadById(String accessToken, String uploadId) throws IOException {
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("uploadId required");
        }
        Request req = new Request.Builder()
                .url(buildUrl("/devices/uploads/" + uploadId))
                .header("x-knox-apitoken", accessToken)
                .header("X-KNOX-API-VERSION", apiVersionHeader())
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String raw = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                log.warn(
                        "Get upload {} failed status={} bodySize={}",
                        uploadId,
                        resp.code(),
                        raw.length());
                throw new KnoxApiException(resp.code(), raw, "Get upload failed");
            }
            return mapper.readValue(raw, new TypeReference<Map<String, Object>>() { });
        }
    }
}
