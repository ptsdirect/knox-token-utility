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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Utility class for handling JWT token generation and key management for Knox Guard.
 */
public class KnoxTokenUtility {
    // No static provider registration needed; rely on default JCA providers for EC (SunEC).

    private static final long TEN_YEARS_IN_MILLIS = 315_532_800_000L; // ~10 years

    /**
     * Create a signed device enrollment JWT (not currently used by CLI but retained for completeness).
     * @param clientId Knox Guard client ID (used as kid & subject)
     * @param imei Device IMEI
     * @param publicKeyBase64 Base64 DER encoded public key (added to x5c header)
     * @param privateKeyInputStream Private key stream (EC P-256 PKCS#8)
     */
    public static String createSignedJWT(String clientId, String imei, String publicKeyBase64, InputStream privateKeyInputStream) {
        try (InputStream is = privateKeyInputStream) {
            PrivateKey privateKey = loadPrivateKeyFromPem(is);
            return Jwts.builder()
                .header()
                    .add("kid", clientId)
                    .add("x5c", new String[]{publicKeyBase64})
                    .and()
                .subject(clientId)
                .audience().add("kpe_v2").and()
                .issuedAt(Date.from(Instant.now()))
                .claim("imei", imei)
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT: " + e.getMessage(), e);
        }
    }

    /**
     * Generate the client identifier JWT used to request an access token.
     */
    public static String generateSignedClientIdentifierJWT(InputStream privateKeyInputStream, String clientId, String password) {
        try (InputStream is = privateKeyInputStream) {
            PrivateKey privateKey = loadPrivateKeyFromPem(is);
            long now = System.currentTimeMillis();
            return Jwts.builder()
                .header().add("typ", "JWT").and()
                .subject(clientId)
                .audience().add("PTSDIRECT.ORG").and()
                .claim("cdt", now / 1000)
                .claim("scope", "all")
                .issuedAt(new Date(now))
                .expiration(new Date(now + TEN_YEARS_IN_MILLIS))
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT: " + e.getMessage(), e);
        }
    }

    /**
     * Return Base64 DER of a public key given a PEM file path.
     * The PEM may contain header/footer lines; they will be stripped.
     */
    public static String getPublicKeyBase64(Path publicKeyPath) {
        try {
            if (!Files.exists(publicKeyPath)) {
                throw new IllegalArgumentException("Public key file not found: " + publicKeyPath);
            }
            String pem = Files.readString(publicKeyPath, StandardCharsets.UTF_8);
            String base64 = stripPemToBase64(pem);
            byte[] der = Base64.getDecoder().decode(base64);
            // Validate by attempting to construct a PublicKey (ensures format correctness)
            KeyFactory kf = KeyFactory.getInstance("EC");
            PublicKey pk = kf.generatePublic(new X509EncodedKeySpec(der));
            return Base64.getEncoder().encodeToString(pk.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read public key: " + e.getMessage(), e);
        }
    }

    private static String stripPemToBase64(String pemContent) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(pemContent.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            boolean inBody = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("BEGIN")) { inBody = true; continue; }
                if (line.contains("END")) { inBody = false; continue; }
                if (inBody) sb.append(line.trim());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse PEM: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    /**
     * Load EC private key (PKCS#8) from PEM InputStream.
     */
    private static PrivateKey loadPrivateKeyFromPem(InputStream pemInputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pemInputStream, StandardCharsets.UTF_8))) {
            String pemContent = reader.lines().collect(Collectors.joining("\n"));
            String base64 = stripPemToBase64(pemContent);
            byte[] encoded = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("EC");
            return kf.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key: " + e.getMessage(), e);
        }
    }

    /**
     * Deprecated legacy method retained temporarily to satisfy stale compiled references.
     * It never actually returned a public key; callers should migrate to {@link #getPublicKeyBase64(Path)}.
     * This stub throws UnsupportedOperationException if invoked at runtime to surface improper usage.
     * TODO: Remove after confirming no lingering references in build output / CI.
     * @deprecated use getPublicKeyBase64(Path)
     */
    @Deprecated
    public static String getPublicKeyFromPrivateKey(InputStream ignored) {
        throw new UnsupportedOperationException("getPublicKeyFromPrivateKey is deprecated. Use getPublicKeyBase64(Path) with the public key path.");
    }
}
