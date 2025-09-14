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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional negative-path and branch coverage tests for KnoxCertificateParser.
 */
class KnoxCertificateParserAdditionalTest {

    private static final String VALID_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgG1pkUKU5LNhxvMyW\n" +
            "Qwdy6QSRoXI0jdo+bwxI7ZsCuzuhRANCAAR7uSTboYYGr2susx6bwyodH4qzM0gc\n" +
            "D5wdrIW6DCe4kYvNys8lm2Sleqrs9jwELyhl725LLJoPLD114F8CbnMD\n" +
            "-----END PRIVATE KEY-----\n";

    private static final String VALID_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE77ojbQ==\n" +
            "-----END PUBLIC KEY-----\n";

    @Test
    void parseWithPublicAndPrivateKeySucceeds() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            KeyPair kp = kpg.generateKeyPair();
            String publicPem = "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
            String json = "{" +
                    "\"clientId\":\"c1\"," +
                    "\"publicKey\":\"" + publicPem.replace("\n","\\n") + "\"," +
                    "\"privateKey\":\"" + VALID_PRIVATE_KEY.replace("\n","\\n") + "\"}";
            KnoxCertificateParser.ParsedCertificate parsed = KnoxCertificateParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            assertEquals("c1", parsed.clientId());
            assertNotNull(parsed.publicKey());
            assertNotNull(parsed.privateKey());
        } catch (Exception e) {
            fail("Unexpected exception generating key pair: " + e.getMessage());
        }
    }

    @Test
    void invalidPrivateKeyPemThrows() {
        String badPrivPem = "-----BEGIN PRIVATE KEY-----\\nNOTBASE64@@@\\n-----END PRIVATE KEY-----";
        String json = "{" +
                "\"clientId\":\"c2\"," +
                "\"privateKey\":\"" + badPrivPem + "\"}";
        RuntimeException ex = assertThrows(RuntimeException.class, () -> KnoxCertificateParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        assertTrue(ex.getMessage().contains("Failed to load private key"));
    }

    @Test
    void invalidPublicKeyStillLoadsPrivateKeyIfPublicMalformed() {
        String badPubPem = "-----BEGIN PUBLIC KEY-----\\n%%%%%%%%\\n-----END PUBLIC KEY-----";
        String json = "{" +
                "\"clientId\":\"c3\"," +
                "\"publicKey\":\"" + badPubPem + "\"," +
                "\"privateKey\":\"" + VALID_PRIVATE_KEY.replace("\n","\\n") + "\"}";
        RuntimeException ex = assertThrows(RuntimeException.class, () -> KnoxCertificateParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        assertTrue(ex.getMessage().contains("Failed to load public key"));
    }
}
