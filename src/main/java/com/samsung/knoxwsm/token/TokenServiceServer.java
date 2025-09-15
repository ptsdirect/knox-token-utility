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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Minimal standalone HTTP server exposing a single endpoint:
 *   POST /api/token  {"clientId":"...","deviceImei":"..."}
 * Responds with {"jwt":"<signed enrollment jwt>"}
 *
 * This is intentionally lightweight (no external frameworks) for quick local experimentation.
 */
public class TokenServiceServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path keyPath = Path.of(System.getenv().getOrDefault("PRIVATE_KEY_PATH", "private_key.pem"));
        if (!Files.exists(keyPath)) {
            System.err.println("Private key file not found: " + keyPath + "\nGenerate one via CLI: java -jar <jar> --mode generate-keys");
            System.exit(1);
        }
        String privateKeyPem = Files.readString(keyPath, StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/token", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, "Method Not Allowed"); return; }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String clientId = extractJson(body, "clientId", System.getenv().getOrDefault("KNOX_GUARD_CLIENT_ID", "client-123"));
            String imei = extractJson(body, "deviceId", System.getenv().getOrDefault("DEVICE_IMEI", "356544761873907"));
            String jwt;
            try {
                // Use quick helper if available; fallback to signed JWT with placeholder public key
                // createEnrollmentJwt already adds placeholder x5c in this codebase
                // Build minimal demo enrollment-style JWT locally (placeholder x5c, not production-valid)
                jwt = buildDemoEnrollmentJwt(clientId, imei, privateKeyPem);
            } catch (Exception e) {
                send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
                return;
            }
            send(exchange, 200, "{\"jwt\":\"" + escape(jwt) + "\"}");
        });
        server.start();
        System.out.println("[" + Instant.now() + "] TokenServiceServer listening on http://localhost:" + port + "/api/token");
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // Extremely small JSON extractor (assumes flat JSON with simple quoted values)
    private static String extractJson(String body, String key, String defaultVal) {
        String pattern = "\"" + key + "\"\s*:\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(body);
        return m.find() ? m.group(1) : defaultVal;
    }

    private static String escape(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // Local helper to construct a minimal ES256 enrollment-style JWT (for demo server only)
    private static String buildDemoEnrollmentJwt(String clientId, String imei, String privateKeyPem) throws Exception {
        String base64 = privateKeyPem.replaceAll("-----BEGIN (.*)-----", "")
            .replaceAll("-----END (.*)-----", "")
            .replaceAll("\r", "")
            .replaceAll("\n", "");
        byte[] der = java.util.Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        PrivateKey pk = KeyFactory.getInstance("EC").generatePrivate(spec);
        return Jwts.builder()
            .header().add("kid", clientId).add("x5c", new String[]{""}).and()
            .subject(clientId)
            .audience().add("kpe_v2").and()
            .issuedAt(new java.util.Date())
            .claim("imei", imei)
            .signWith(pk, SignatureAlgorithm.ES256)
            .compact();
    }
}
