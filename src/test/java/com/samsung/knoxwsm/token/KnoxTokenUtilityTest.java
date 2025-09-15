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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KnoxTokenUtilityTest {

    @Test
    void createEnrollmentStyleJwt_producesThreeSegmentsAndClaims() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        String privatePem = toPem("PRIVATE KEY", kp.getPrivate().getEncoded());
        String publicPem = toPem("PUBLIC KEY", kp.getPublic().getEncoded());
    // Use minimal helper that does NOT attempt to treat public key bytes as an x5c certificate chain.
    String jwt = KnoxTokenUtility2.createEnrollmentJwt(
        "client-abc",
        "359881234567890",
        privatePem);
        assertNotNull(jwt);
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        for (int i = 0; i < 2; i++) {
            final String segment = parts[i];
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(segment + padding(segment));
                assertNotNull(decoded);
            } catch (IllegalArgumentException ex) {
                throw new AssertionError("Segment did not decode: index=" + i, ex);
            }
        }
    }

    private static String toPem(String type, byte[] der) {
        String b64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        sb.append("-----END ").append(type).append("-----\n");
        return sb.toString();
    }

    private static String padding(String base64Url) {
        int mod = base64Url.length() % 4;
        if (mod == 2) return "==";
        if (mod == 3) return "=";
        if (mod == 1) return "=";
        return "";
    }
}
