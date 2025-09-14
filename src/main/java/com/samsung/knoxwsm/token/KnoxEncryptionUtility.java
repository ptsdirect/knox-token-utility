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

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility for RSA encryption of small plaintexts (<= 245 bytes for 2048-bit RSA with PKCS#1 v1.5 padding).
 * Knox Guard spec mentions RSA usage for limited-size sensitive values.
 */
public final class KnoxEncryptionUtility {
    private KnoxEncryptionUtility() {}

    private static final int MAX_RSA_PLAINTEXT = 245; // for 2048-bit key w/ PKCS#1 v1.5

    public static String encryptSmallPayload(String publicKeyPem, String plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("plaintext cannot be null");
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_RSA_PLAINTEXT) {
            throw new IllegalArgumentException("Plaintext length " + data.length + " exceeds RSA limit of " + MAX_RSA_PLAINTEXT + " bytes");
        }
        try {
            PublicKey pk = loadRsaPublic(publicKeyPem);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pk);
            byte[] encrypted = cipher.doFinal(data);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to RSA encrypt payload: " + e.getMessage(), e);
        }
    }

    private static PublicKey loadRsaPublic(String pem) throws Exception {
        String base64 = stripPem(pem);
        byte[] der = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String stripPem(String pem) throws Exception {
        String[] lines = pem.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        boolean inBody = false;
        for (String line : lines) {
            if (line.contains("BEGIN")) { inBody = true; continue; }
            if (line.contains("END")) { inBody = false; continue; }
            if (inBody) sb.append(line.trim());
        }
        return sb.toString();
    }
}
