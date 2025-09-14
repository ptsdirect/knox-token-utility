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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple configuration loader that reads environment variables first, then a local .env file (KEY=VALUE lines).
 */
public final class Config {
    private static final Map<String, String> ENV_CACHE = new HashMap<>();
    private static boolean loadedDotEnv = false;

    private Config() {}

    private static synchronized void loadDotEnvIfNeeded() {
        if (loadedDotEnv) return;
        Path envPath = Paths.get(".env");
        if (Files.exists(envPath)) {
            try {
                for (String line : Files.readAllLines(envPath)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) continue;
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    // Don't overwrite real environment variables
                    if (System.getenv(key) == null) {
                        ENV_CACHE.putIfAbsent(key, stripQuotes(value));
                    }
                }
            } catch (IOException ignored) {}
        }
        loadedDotEnv = true;
    }

    private static String stripQuotes(String v) {
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    public static String get(String key, String defaultValue) {
        loadDotEnvIfNeeded();
        String env = System.getenv(key);
        if (env != null && !env.isEmpty()) return env;
        return ENV_CACHE.getOrDefault(key, defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        String raw = get(key, null);
        if (raw == null) return defaultValue;
        try { return Long.parseLong(raw); } catch (NumberFormatException e) { return defaultValue; }
    }
}
