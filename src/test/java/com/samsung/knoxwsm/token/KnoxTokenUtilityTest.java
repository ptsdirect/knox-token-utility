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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

class KnoxTokenUtilityTest {
    private KeyPair keyPair;
    private String clientIdentifier;
    private String privateKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        keyPair = keyGen.generateKeyPair();
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()) +
            "\n-----END PRIVATE KEY-----\n";
        clientIdentifier = "test-client-" + System.currentTimeMillis();
    }

    @Test
    void testGenerateSignedClientIdentifierJWT() throws Exception {
        InputStream keyInputStream = new ByteArrayInputStream(privateKeyPem.getBytes());
        String jwt = KnoxTokenUtility.generateSignedClientIdentifierJWT(keyInputStream, clientIdentifier, null);
        assertNotNull(jwt);
        assertFalse(jwt.isEmpty());
        Claims claims = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(jwt).getPayload();
        assertEquals(clientIdentifier, claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void testGenerateJWTWithInvalidKey() {
        String invalidKey = "invalid-key-format";
        InputStream invalidKeyStream = new ByteArrayInputStream(invalidKey.getBytes());
        Exception exception = assertThrows(RuntimeException.class, () -> {
            KnoxTokenUtility.generateSignedClientIdentifierJWT(invalidKeyStream, clientIdentifier, null);
        });
        assertTrue(exception.getMessage().contains("Failed to generate JWT"));
    }

    private String certificateJson() {
        return "{" +
            "\"clientId\":\"" + clientIdentifier + "\"," +
            "\"privateKey\":\"" + privateKeyPem.replace("\n","\\n") + "\"}";
    }

    @Test
    void testCertificateBasedClientIdentifier() {
        InputStream cert = new ByteArrayInputStream(certificateJson().getBytes());
        String jwt = KnoxCertificateJwtUtility.generateSignedClientIdentifierJWTWithIdpAccessToken(cert, clientIdentifier, "idpTokenXYZ");
        assertNotNull(jwt);
        assertEquals(3, jwt.split("\\.").length);
    }

    @Test
    void testSessionTokenJwt() {
        InputStream cert = new ByteArrayInputStream(certificateJson().getBytes());
        String jwt = KnoxCertificateJwtUtility.generateSignedSessionTokenJWT(cert, "session-raw-token");
        assertNotNull(jwt);
    }

    @Test
    void testAccessTokenJwt() {
        InputStream cert = new ByteArrayInputStream(certificateJson().getBytes());
        String jwt = KnoxCertificateJwtUtility.generateSignedAccessTokenJWT(cert, "access-raw-token");
        assertNotNull(jwt);
    }
}
