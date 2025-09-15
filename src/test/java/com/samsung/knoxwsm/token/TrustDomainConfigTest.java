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
import java.util.List;
import java.util.Set;

class TrustDomainConfigTest {

    @Test
    void testDefaultConfiguration() {
        TrustDomainConfig config = TrustDomainConfig.createDefault();
        
        assertNotNull(config);
        assertEquals("https://api.samsungknox.com", config.getPrimaryTrustDomain());
        assertEquals("https://trust.mdttee.com", config.getSecondaryTrustDomain());
        assertEquals("artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa", 
                     config.getArtifactMetadata());
    }

    @Test
    void testCustomConfiguration() {
        List<String> domains = List.of("https://custom1.com", "https://custom2.com");
        String metadata = "artifact=com.example:test;sig=RSA;sbom=cyclonedx;prov=github";
        
        TrustDomainConfig config = TrustDomainConfig.create(domains, metadata);
        
        assertEquals("https://custom1.com", config.getPrimaryTrustDomain());
        assertEquals("https://custom2.com", config.getSecondaryTrustDomain());
        assertEquals(metadata, config.getArtifactMetadata());
    }

    @Test
    void testTrustedDomainValidation() {
        TrustDomainConfig config = TrustDomainConfig.createDefault();
        
        assertTrue(config.isTrustedDomain("https://api.samsungknox.com/kcs/v1"));
        assertTrue(config.isTrustedDomain("https://trust.mdttee.com/api"));
        assertFalse(config.isTrustedDomain("https://malicious.com"));
        assertFalse(config.isTrustedDomain(null));
    }

    @Test
    void testArtifactMetadataParsing() {
        String metadata = "artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa";
        TrustDomainConfig.ArtifactMetadata parsed = TrustDomainConfig.ArtifactMetadata.parse(metadata);
        
        assertEquals("com.mdttee.knox:pts", parsed.getArtifact());
        assertEquals("GPG", parsed.getSignature());
        assertEquals(Set.of("cyclonedx", "spdx"), parsed.getSbomFormats());
        assertEquals("slsa", parsed.getProvenance());
    }

    @Test
    void testArtifactMetadataToString() {
        String original = "artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa";
        TrustDomainConfig.ArtifactMetadata parsed = TrustDomainConfig.ArtifactMetadata.parse(original);
        String rebuilt = parsed.toString();
        
        // Order might be different, so check components
        assertTrue(rebuilt.contains("artifact=com.mdttee.knox:pts"));
        assertTrue(rebuilt.contains("sig=GPG"));
        assertTrue(rebuilt.contains("prov=slsa"));
        assertTrue(rebuilt.contains("sbom=") && (rebuilt.contains("cyclonedx") && rebuilt.contains("spdx")));
    }

    @Test
    void testEmptyDomainsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            TrustDomainConfig.create(List.of(), "metadata");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TrustDomainConfig.create(null, "metadata");
        });
    }

    @Test
    void testNullMetadataUsesDefault() {
        List<String> domains = List.of("https://test.com");
        TrustDomainConfig config = TrustDomainConfig.create(domains, null);
        
        assertEquals("artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa", 
                     config.getArtifactMetadata());
    }
}