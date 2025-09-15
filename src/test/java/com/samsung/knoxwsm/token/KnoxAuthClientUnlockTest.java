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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

/** Tests for unlockDevice and enrollAndUnlock convenience helper. */
public class KnoxAuthClientUnlockTest {
    private MockWebServer server;
    private KnoxAuthClient client;

    private static final String ACCESS = "at.jwt.value";
    private static final String CLIENT_ID = "client-xyz";
    private static final String IMEI = "359881234567890";

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/kcs/v1").toString().replaceAll("/+$$", "");
        String guardBase = server.url("/kcs/v1.1/kg").toString().replaceAll("/+$$", "");
        client = new KnoxAuthClient(base, guardBase);
    }

    @AfterEach
    void stop() throws IOException { server.shutdown(); }

    @Test
    void unlockDevice_success() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"unlocked\":true}"));
        Map<String,Object> resp = client.unlockDevice(ACCESS, IMEI);
        assertEquals(Boolean.TRUE, resp.get("unlocked"));
        RecordedRequest r = server.takeRequest();
        assertEquals("POST", r.getMethod());
        assertTrue(r.getPath().endsWith("/kguard/devices/unlock"));
    }

    @Test
    void unlockDevice_error() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":\"DENIED\"}"));
        KnoxApiException ex = assertThrows(KnoxApiException.class, () -> client.unlockDevice(ACCESS, IMEI));
        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.getErrorBody().contains("DENIED"));
    }

    @Test
    void enrollAndUnlock_success() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"enrolled\":true}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"unlocked\":true}"));
        Map<String,Object> combo = client.enrollAndUnlock(ACCESS, IMEI, CLIENT_ID);
        Map<String,Object> enroll = (Map<String,Object>) combo.get("enrollment");
        Map<String,Object> unlock = (Map<String,Object>) combo.get("unlock");
        assertEquals(Boolean.TRUE, enroll.get("enrolled"));
        assertEquals(Boolean.TRUE, unlock.get("unlocked"));
        RecordedRequest r1 = server.takeRequest();
        RecordedRequest r2 = server.takeRequest();
        assertEquals("/kcs/v1/kguard/devices", r1.getPath());
        assertTrue(r2.getPath().endsWith("/kguard/devices/unlock"));
    }

    @Test
    void enrollAndUnlock_unlockFails() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"enrolled\":true}"));
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":\"NOT_FOUND\"}"));
        KnoxApiException ex = assertThrows(KnoxApiException.class, () -> client.enrollAndUnlock(ACCESS, IMEI, CLIENT_ID));
        assertEquals(404, ex.getStatusCode());
    }
}
