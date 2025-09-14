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
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class KnoxCertificateParserTest {

    // Valid PKCS#8 EC private key for curve P-256 generated solely for test (non-sensitive dummy)
    private static final String EC_PRIVATE_PEM = """
-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgG1pkUKU5LNhxvMyW
Qwdy6QSRoXI0jdo+bwxI7ZsCuzuhRANCAAR7uSTboYYGr2susx6bwyodH4qzM0gc
D5wdrIW6DCe4kYvNys8lm2Sleqrs9jwELyhl725LLJoPLD114F8CbnMD
-----END PRIVATE KEY-----
""";

    // Minimal valid EC public key (not necessarily matching above) for parsing demonstration
    private static final String EC_PUBLIC_PEM = """
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE77ojbQ==
-----END PUBLIC KEY-----
"""; // not a matching key pair intentionally; parser doesn't validate key agreement

    @Test
    void parseFailsWithoutClientId() {
        String json = "{" +
                "\"privateKey\":\"" + EC_PRIVATE_PEM.replace("\n","\\n") + "\"}";
        ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> KnoxCertificateParser.parse(bais));
        assertTrue(ex.getMessage().contains("client identifier"));
    }

    @Test
    void parseFailsWithoutPrivateKey() {
        String json = "{" +
                "\"clientId\":\"abc\"}";
        ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> KnoxCertificateParser.parse(bais));
        assertTrue(ex.getMessage().contains("private key"));
    }

    @Test
    void parseMinimalSuccess() {
        String json = "{" +
                "\"clientId\":\"abc123\"," +
                "\"privateKey\":\"" + EC_PRIVATE_PEM.replace("\n","\\n") + "\"}";
        ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        KnoxCertificateParser.ParsedCertificate parsed = KnoxCertificateParser.parse(bais);
        assertEquals("abc123", parsed.clientId());
        assertNotNull(parsed.privateKey());
    }
}
