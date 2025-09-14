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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class KnoxTokenUtilityCreateJwtTest {
    @Test
    void testCreateSignedJWTExpectRuntimeDueToX5C() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        KeyPair kp = gen.generateKeyPair();
        String privatePem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        String publicBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            KnoxTokenUtility.createSignedJWT("client-xyz", "359881234567890", publicBase64, new ByteArrayInputStream(privatePem.getBytes()))
        );
        assertTrue(ex.getMessage().contains("Failed to create JWT"));
    }
}
