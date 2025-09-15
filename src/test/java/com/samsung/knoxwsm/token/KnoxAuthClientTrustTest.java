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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnoxAuthClientTrustTest {

    @Test
    void testDefaultTrustConfiguration() {
        KnoxAuthClient client = new KnoxAuthClient();
        
        assertNotNull(client.getTrustConfig());
        assertEquals("https://api.samsungknox.com", client.getTrustConfig().getPrimaryTrustDomain());
        assertEquals("https://trust.mdttee.com", client.getTrustConfig().getSecondaryTrustDomain());
    }

    @Test
    void testCustomTrustConfiguration() {
        TrustDomainConfig config = TrustDomainConfig.createDefault();
        KnoxAuthClient client = new KnoxAuthClient("https://test.com/kcs/v1", config);
        
        assertEquals(config, client.getTrustConfig());
    }

    @Test
    void testArtifactMetadata() {
        KnoxAuthClient client = new KnoxAuthClient();
        String metadata = client.getArtifactMetadata();
        
        assertNotNull(metadata);
        assertTrue(metadata.contains("artifact=com.mdttee.knox:pts"));
        assertTrue(metadata.contains("sig=GPG"));
        assertTrue(metadata.contains("sbom=cyclonedx,spdx"));
        assertTrue(metadata.contains("prov=slsa"));
    }

    @Test
    void testTrustedUrlValidation() {
        KnoxAuthClient client = new KnoxAuthClient();
        
        assertTrue(client.isTrustedUrl("https://api.samsungknox.com/kcs/v1"));
        assertTrue(client.isTrustedUrl("https://trust.mdttee.com/api"));
        assertFalse(client.isTrustedUrl("https://malicious.com"));
        assertFalse(client.isTrustedUrl(null));
    }

    @Test
    void testSecondaryDomainRequest() {
        KnoxAuthClient client = new KnoxAuthClient();
        
        // Should throw IOException when trying to connect to unreachable domain
        assertThrows(Exception.class, () -> {
            client.requestAccessTokenFromSecondaryDomain("publicKey", "jwt", 30);
        });
    }

    @Test
    void testNullTrustConfigHandling() {
        KnoxAuthClient client = new KnoxAuthClient("https://test.com", null);
        
        // Should use default config when null is passed
        assertNotNull(client.getTrustConfig());
        assertEquals("https://api.samsungknox.com", client.getTrustConfig().getPrimaryTrustDomain());
    }
}