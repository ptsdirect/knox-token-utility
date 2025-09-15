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

/**
 * Demo class to showcase the new trust domain functionality.
 */
public class TrustDomainDemo {
    public static void main(String[] args) {
        System.out.println("=== Knox Trust Domain Configuration Demo ===\n");
        
        // Show default configuration
        TrustDomainConfig defaultConfig = TrustDomainConfig.createDefault();
        System.out.println("Default Trust Configuration:");
        System.out.println("  Primary Domain: " + defaultConfig.getPrimaryTrustDomain());
        System.out.println("  Secondary Domain: " + defaultConfig.getSecondaryTrustDomain());
        System.out.println("  All Trust Domains: " + defaultConfig.getTrustDomains());
        System.out.println("  Artifact Metadata: " + defaultConfig.getArtifactMetadata());
        
        // Parse and display artifact metadata
        TrustDomainConfig.ArtifactMetadata metadata = defaultConfig.parseArtifactMetadata();
        System.out.println("\nParsed Artifact Metadata:");
        System.out.println("  Artifact: " + metadata.getArtifact());
        System.out.println("  Signature: " + metadata.getSignature());
        System.out.println("  SBOM Formats: " + metadata.getSbomFormats());
        System.out.println("  Provenance: " + metadata.getProvenance());
        
        // Test trust domain validation
        System.out.println("\nTrust Domain Validation:");
        String[] testUrls = {
            "https://api.samsungknox.com/kcs/v1/ses/token",
            "https://trust.mdttee.com/api/verify",
            "https://malicious.example.com/attack",
            "https://api.samsungknox.com.evil.com/fake"
        };
        
        for (String url : testUrls) {
            boolean trusted = defaultConfig.isTrustedDomain(url);
            System.out.println("  " + url + " -> " + (trusted ? "TRUSTED" : "NOT TRUSTED"));
        }
        
        // Show Knox Auth Client with trust configuration
        System.out.println("\nKnox Auth Client Configuration:");
        KnoxAuthClient client = new KnoxAuthClient();
        TrustDomainConfig clientConfig = client.getTrustConfig();
        System.out.println("  Client Primary Domain: " + clientConfig.getPrimaryTrustDomain());
        System.out.println("  Client Secondary Domain: " + clientConfig.getSecondaryTrustDomain());
        System.out.println("  Client Artifact Metadata: " + client.getArtifactMetadata());
        
        // Test URL validation through client
        System.out.println("\nClient URL Validation:");
        for (String url : testUrls) {
            boolean trusted = client.isTrustedUrl(url);
            System.out.println("  " + url + " -> " + (trusted ? "TRUSTED" : "NOT TRUSTED"));
        }
        
        System.out.println("\n=== Demo Complete ===");
    }
}