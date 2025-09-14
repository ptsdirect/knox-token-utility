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
import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class KnoxEncryptionUtilityTest {

    @Test
    void encryptSmallPayloadSuccess() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String publicPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        String encrypted = KnoxEncryptionUtility.encryptSmallPayload(publicPem, "hello");
        assertNotNull(encrypted);
        assertFalse(encrypted.isBlank());
    }

    @Test
    void encryptTooLargeFails() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String publicPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('A');
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> KnoxEncryptionUtility.encryptSmallPayload(publicPem, sb.toString()));
        assertTrue(ex.getMessage().contains("exceeds"));
    }
}
