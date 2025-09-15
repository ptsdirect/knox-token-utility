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

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

/**
 * Utility for RSA encryption of small plaintexts ({@literal <=} 245 bytes for a 2048-bit key with
 * PKCS#1 v1.5 padding). Intended only for very small secrets (session seeds, short tokens).
 * <p>Do not use for bulk data. Consider hybrid encryption (RSA + AES) if size grows.</p>
 */
/**
 * Utility for RSA encryption of small plaintexts ({@literal <=} 245 bytes for a 2048-bit key with PKCS#1 v1.5 padding).
 * Intended only for very small secrets (session seeds, short tokens).
 * <p>Do not use for bulk data. Consider hybrid encryption (RSA + AES) if size grows.</p>
 */
public final class KnoxEncryptionUtility {

    private KnoxEncryptionUtility() {
    }

    /** Max bytes for plaintext using 2048-bit RSA + PKCS#1 v1.5. */
    private static final int MAX_RSA_PLAINTEXT = 245;

    /**
     * Encrypt a small UTF-8 string with an RSA public key (PEM, X.509 SubjectPublicKeyInfo).
     *
     * @param publicKeyPem PEM string including BEGIN/END lines
    * @param plaintext small UTF-8 plaintext (must be {@literal <=} 245 bytes)
     * @return Base64 encoded ciphertext
     * @throws IllegalArgumentException if inputs invalid or size exceeded
     * @throws RuntimeException         wrapping any cryptographic failure
     */
    public static String encryptSmallPayload(final String publicKeyPem, final String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext cannot be null");
        }
        final byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_RSA_PLAINTEXT) {
            throw new IllegalArgumentException(
                "Plaintext length " + data.length + " exceeds RSA limit of " + MAX_RSA_PLAINTEXT + " bytes"
            );
        }
        try {
            final PublicKey pk = loadRsaPublic(publicKeyPem);
            final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pk);
            final byte[] encrypted = cipher.doFinal(data);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to RSA encrypt payload: " + e.getMessage(), e);
        }
    }

    private static PublicKey loadRsaPublic(final String pem) throws Exception {
        final String base64 = stripPem(pem);
        final byte[] der = Base64.getDecoder().decode(base64);
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String stripPem(final String pem) {
        final String[] lines = pem.split("\r?\n");
        final StringBuilder sb = new StringBuilder();
        boolean inBody = false;
        for (String line : lines) {
            if (line.contains("BEGIN")) {
                inBody = true;
                continue;
            }
            if (line.contains("END")) {
                inBody = false;
                continue;
            }
            if (inBody) {
                sb.append(line.trim());
            }
        }
        return sb.toString();
    }
}
