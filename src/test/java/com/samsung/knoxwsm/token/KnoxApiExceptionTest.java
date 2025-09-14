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

class KnoxApiExceptionTest {
    @Test
    void testGetters() {
        KnoxApiException ex = new KnoxApiException(404, "Not Found", "Check URL");
        assertEquals(404, ex.getStatusCode());
        assertEquals("Not Found", ex.getErrorBody());
        assertEquals("Check URL", ex.getSuggestion());
    }
}
