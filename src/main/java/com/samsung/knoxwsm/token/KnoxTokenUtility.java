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
  private static final long TEN_YEARS_IN_MILLIS = 315_532_800_000L; // ~10 years
  // Marker constant to verify recompilation source alignment. If absent in compiled class, stale build path issue persists.
  public static final String BUILD_MARKER = "BUILD_MARKER_V1";

  /**
   * Convenience wrapper for minimal enrollment JWT: createEnrollmentJwt(clientId, deviceImei, privateKeyPem).
   * This delegates to a minimal builder without x5c chain for local experiments.
   */
  public static String createEnrollmentJwt(String clientId, String deviceImei, String privateKeyPem) {
    return createMinimalSignedJwt(clientId, deviceImei, privateKeyPem);
  }

  private static String createMinimalSignedJwt(String clientId, String deviceImei, String privateKeyPem) {
    try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.UTF_8))) {
      return Jwts.builder()
        .header().add("kid", clientId).and()
        .subject(clientId)
        .audience().add("kpe_v2").and()
        .issuedAt(new Date())
        .claim("imei", deviceImei)
        .signWith(loadPrivateKeyFromPem(bais), SignatureAlgorithm.ES256)
        .compact();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create enrollment JWT: " + e.getMessage(), e);
    }
  }

  /**
   * Create a signed device enrollment JWT (with optional x5c chain element).
   */
  public static String createSignedJWT(String clientId, String imei, String publicKeyBase64, InputStream privateKeyInputStream) {
    try (InputStream is = privateKeyInputStream) {
      PrivateKey privateKey = loadPrivateKeyFromPem(is);
      io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
        .header().add("kid", clientId).and()
        .subject(clientId)
        .audience().add("kpe_v2").and()
        .issuedAt(Date.from(Instant.now()))
        .claim("imei", imei);
      if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
        builder = builder.header().add("x5c", new String[]{publicKeyBase64}).and();
      }
      return builder.signWith(privateKey, SignatureAlgorithm.ES256).compact();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create JWT: " + e.getMessage(), e);
    }
  }

  public static String generateSignedClientIdentifierJWTWithIdpAccessToken(InputStream certificateJson, String clientIdentifier, String idpAccessToken) {
    KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
    long now = System.currentTimeMillis();
    String atHash = (idpAccessToken == null ? null : Integer.toHexString(idpAccessToken.hashCode()));
    return Jwts.builder()
      .header().add("typ", "JWT").and()
      .subject(clientIdentifier)
      .audience().add("PTSDIRECT.ORG").and()
      .claim("cdt", now / 1000)
      .claim("scope", "all")
      .claim("idp_at", atHash)
      .issuedAt(new Date(now))
      .expiration(new Date(now + TEN_YEARS_IN_MILLIS))
      .signWith(cert.privateKey(), SignatureAlgorithm.ES256)
      .compact();
  }

  public static String generateSignedSessionTokenJWT(InputStream certificateJson, String sessionToken) {
    KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
    long now = System.currentTimeMillis();
    return Jwts.builder()
      .header().add("typ", "JWT").and()
      .subject("session")
      .audience().add("PTSDIRECT.ORG").and()
      .claim("st", sessionToken)
      .issuedAt(new Date(now))
      .expiration(new Date(now + 3_600_000L))
      .signWith(cert.privateKey(), SignatureAlgorithm.ES256)
      .compact();
  }

  public static String generateSignedAccessTokenJWT(InputStream certificateJson, String accessToken) {
    KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
    long now = System.currentTimeMillis();
    return Jwts.builder()
      .header().add("typ", "JWT").and()
      .subject("access")
      .audience().add("PTSDIRECT.ORG").and()
      .claim("at", accessToken)
      .issuedAt(new Date(now))
      .expiration(new Date(now + 3_600_000L))
      .signWith(cert.privateKey(), SignatureAlgorithm.ES256)
      .compact();
  }

  public static String generateBase64EncodedStringPublicKey(InputStream certificateJson) {
    KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
    PublicKey pk = cert.publicKey();
    if (pk == null) throw new IllegalArgumentException("Certificate JSON did not include a public key field; cannot export public key");
    return Base64.getEncoder().encodeToString(pk.getEncoded());
  }

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

  public static String getPublicKeyBase64(Path publicKeyPath) {
    try {
      if (!Files.exists(publicKeyPath)) {
        throw new IllegalArgumentException("Public key file not found: " + publicKeyPath);
      }
      String pem = Files.readString(publicKeyPath, StandardCharsets.UTF_8);
      String base64 = stripPemToBase64(pem);
      byte[] der = Base64.getDecoder().decode(base64);
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
}
