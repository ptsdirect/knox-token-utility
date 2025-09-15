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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Basic smoke tests for upload-related methods using MockWebServer. */
public class KnoxAuthClientUploadTest {
    private static MockWebServer server;

    @BeforeAll
    static void setup() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void uploadDevices_success() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"accepted\"}"));
        String base = server.url("/kcs/v1").toString().replaceAll("/+$$", "");
        String guardBase = server.url("/kcs/v1.1/kg").toString().replaceAll("/+$$", "");
        KnoxAuthClient client = new KnoxAuthClient(base, guardBase);
        Map<String,Object> resp = client.uploadDevices("dummyAccess", "{\"deviceList\":[]}");
        assertEquals("accepted", resp.get("status"));
    }

    @Test
    void listUploads_success() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"total\":0}"));
        String base = server.url("/kcs/v1").toString().replaceAll("/+$$", "");
        String guardBase = server.url("/kcs/v1.1/kg").toString().replaceAll("/+$$", "");
        KnoxAuthClient client = new KnoxAuthClient(base, guardBase);
        Map<String,Object> resp = client.listDeviceUploads("dummyAccess");
        assertEquals(0, ((Number)resp.get("total")).intValue());
    }

    @Test
    void getUploadById_success() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"u123\"}"));
        String base = server.url("/kcs/v1").toString().replaceAll("/+$$", "");
        String guardBase = server.url("/kcs/v1.1/kg").toString().replaceAll("/+$$", "");
        KnoxAuthClient client = new KnoxAuthClient(base, guardBase);
        Map<String,Object> resp = client.getUploadById("dummyAccess", "u123");
        assertEquals("u123", resp.get("id"));
    }

    @Test
    void uploadDevices_error() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid\"}"));
        String base = server.url("/kcs/v1").toString().replaceAll("/+$$", "");
        String guardBase = server.url("/kcs/v1.1/kg").toString().replaceAll("/+$$", "");
        KnoxAuthClient client = new KnoxAuthClient(base, guardBase);
        KnoxApiException ex = assertThrows(KnoxApiException.class, () -> client.uploadDevices("token", "{}"));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getErrorBody().contains("invalid"));
    }
}
