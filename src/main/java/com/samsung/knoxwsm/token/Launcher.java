package com.samsung.knoxwsm.token;

/*-
 * #%L
 * Knox Guard Token Utility
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

/**
 * Unified entry point.
 * Usage:
 *   java -jar pts-...-jar-with-dependencies.jar            (delegates to TokenClient with passed args)
 *   java -jar pts-...-jar-with-dependencies.jar server     (starts TokenServiceServer)
 *   java -jar pts-...-jar-with-dependencies.jar server 9090 (custom port via PORT env or second arg)
 */
public class Launcher {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equalsIgnoreCase("server")) {
            // Optional second arg = port
            if (args.length > 1) {
                System.setProperty("PORT", args[1]); // TokenServiceServer reads env; allow system property fallback
            }
            TokenServiceServer.main(new String[0]);
            return;
        }
        // Delegate to TokenClient main directly
        TokenClient.main(args);
    }
}
