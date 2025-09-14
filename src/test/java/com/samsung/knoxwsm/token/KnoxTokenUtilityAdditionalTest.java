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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnoxTokenUtilityAdditionalTest {

    @Test
    void getPublicKeyBase64MissingFileThrows() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                KnoxTokenUtility.getPublicKeyBase64(Path.of("nonexistent-" + UUID.randomUUID() + ".pem"))
        );
        assertTrue(ex.getMessage().contains("Public key file not found"));
    }

    @Test
    void generateSignedClientIdentifierJWTRawInvalidPemEmpty() {
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                KnoxTokenUtility.generateSignedClientIdentifierJWT(empty, "clientX", null)
        );
        assertTrue(ex.getMessage().contains("Failed to generate JWT"));
    }
}
