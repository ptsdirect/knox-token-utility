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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Parser for the flexible {@code certificate.json} structure. Loads in-memory private &amp; public key objects.
 */
public final class KnoxCertificateParser {
    private static final ObjectMapper OM = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private KnoxCertificateParser() {}

    public record ParsedCertificate(String clientId, PublicKey publicKey, PrivateKey privateKey) {}

    public static ParsedCertificate parse(InputStream certificateJson) {
        try (InputStream is = certificateJson) {
            KnoxCertificate cert = OM.readValue(is, KnoxCertificate.class);
            String client = cert.resolvedClientId();
            if (client == null) throw new IllegalArgumentException("Certificate JSON missing client identifier field");
            PublicKey pub = null;
            String pubStr = cert.resolvedPublicKey();
            if (pubStr != null) pub = loadPublic(pubStr);
            String privStr = cert.resolvedPrivateKey();
            if (privStr == null) throw new IllegalArgumentException("Certificate JSON missing private key field");
            PrivateKey pk = loadPrivate(privStr);
            return new ParsedCertificate(client, pub, pk);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse certificate JSON: " + e.getMessage(), e);
        }
    }

    private static PublicKey loadPublic(String pem) {
        try {
            String base64 = stripPem(pem);
            byte[] der = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load public key from certificate JSON: " + e.getMessage(), e);
        }
    }

    private static PrivateKey loadPrivate(String pem) {
        try {
            String base64 = stripPem(pem);
            byte[] der = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key from certificate JSON: " + e.getMessage(), e);
        }
    }

    private static String stripPem(String pem) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; boolean inBody = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("BEGIN")) { inBody = true; continue; }
                if (line.contains("END")) { inBody = false; continue; }
                if (inBody) sb.append(line.trim());
            }
            return sb.toString();
        }
    }
}
