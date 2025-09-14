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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    private Path envFile;

    @BeforeEach
    void setUp() throws IOException {
        envFile = Path.of(".env");
        Files.deleteIfExists(envFile);
        // Reset static state via reflection (since fields are private and static)
        try {
            var loadedField = Config.class.getDeclaredField("loadedDotEnv");
            loadedField.setAccessible(true);
            loadedField.setBoolean(null, false);
            var cacheField = Config.class.getDeclaredField("ENV_CACHE");
            cacheField.setAccessible(true);
            ((java.util.Map<?,?>)cacheField.get(null)).clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(envFile);
        try {
            var loadedField = Config.class.getDeclaredField("loadedDotEnv");
            loadedField.setAccessible(true);
            loadedField.setBoolean(null, false);
            var cacheField = Config.class.getDeclaredField("ENV_CACHE");
            cacheField.setAccessible(true);
            ((java.util.Map<?,?>)cacheField.get(null)).clear();
        } catch (Exception ignored) { }
    }

    @Test
    void testLoadsDotEnvAndStripQuotes() throws IOException {
        Files.writeString(envFile, "FOO=bar\nQUOTED='quoted value'\nDOUBLE=\"double quoted\"\n#COMMENT=skip\nINVALIDLINE\n");
        assertEquals("bar", Config.get("FOO", "missing"));
        assertEquals("quoted value", Config.get("QUOTED", "missing"));
        assertEquals("double quoted", Config.get("DOUBLE", "missing"));
        // default path
        assertEquals("fallback", Config.get("UNSET_KEY", "fallback"));
    }

    @Test
    void testGetLongParsing() throws IOException {
        Files.writeString(envFile, "NUM=123\nBAD=notanumber\n");
        assertEquals(123L, Config.getLong("NUM", 0));
        // invalid number returns default
        assertEquals(50L, Config.getLong("BAD", 50));
        // absent returns default
        assertEquals(77L, Config.getLong("MISSING", 77));
    }
}
