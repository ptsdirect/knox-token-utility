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

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Configuration for trust domains and artifact metadata.
 */
public final class TrustDomainConfig {
    private static final List<String> DEFAULT_TRUST_DOMAINS = List.of(
        "https://api.samsungknox.com",
        "https://trust.mdttee.com"
    );
    
    private static final String DEFAULT_ARTIFACT_METADATA = 
        "artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa";
    
    private final List<String> trustDomains;
    private final String artifactMetadata;
    
    private TrustDomainConfig(List<String> trustDomains, String artifactMetadata) {
        this.trustDomains = new ArrayList<>(trustDomains);
        this.artifactMetadata = artifactMetadata;
    }
    
    /**
     * Create default configuration with Samsung Knox and MDTTEE trust domains.
     */
    public static TrustDomainConfig createDefault() {
        return new TrustDomainConfig(DEFAULT_TRUST_DOMAINS, DEFAULT_ARTIFACT_METADATA);
    }
    
    /**
     * Create configuration with custom trust domains and artifact metadata.
     */
    public static TrustDomainConfig create(List<String> trustDomains, String artifactMetadata) {
        if (trustDomains == null || trustDomains.isEmpty()) {
            throw new IllegalArgumentException("At least one trust domain must be specified");
        }
        return new TrustDomainConfig(trustDomains, 
            artifactMetadata != null ? artifactMetadata : DEFAULT_ARTIFACT_METADATA);
    }
    
    /**
     * Get list of configured trust domains.
     */
    public List<String> getTrustDomains() {
        return new ArrayList<>(trustDomains);
    }
    
    /**
     * Get the primary (first) trust domain.
     */
    public String getPrimaryTrustDomain() {
        return trustDomains.get(0);
    }
    
    /**
     * Get the secondary trust domain (trust.mdttee.com).
     */
    public String getSecondaryTrustDomain() {
        return trustDomains.size() > 1 ? trustDomains.get(1) : null;
    }
    
    /**
     * Check if a domain is trusted.
     */
    public boolean isTrustedDomain(String domain) {
        if (domain == null) return false;
        return trustDomains.stream().anyMatch(trusted -> 
            domain.startsWith(trusted) || trusted.contains(extractDomain(domain)));
    }
    
    private String extractDomain(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            int start = url.indexOf("://") + 3;
            int end = url.indexOf('/', start);
            return end > 0 ? url.substring(start, end) : url.substring(start);
        }
        return url;
    }
    
    /**
     * Get artifact metadata configuration.
     */
    public String getArtifactMetadata() {
        return artifactMetadata;
    }
    
    /**
     * Parse artifact metadata into components.
     */
    public ArtifactMetadata parseArtifactMetadata() {
        return ArtifactMetadata.parse(artifactMetadata);
    }
    
    /**
     * Represents parsed artifact metadata.
     */
    public static class ArtifactMetadata {
        private final String artifact;
        private final String signature;
        private final Set<String> sbomFormats;
        private final String provenance;
        
        private ArtifactMetadata(String artifact, String signature, 
                               Set<String> sbomFormats, String provenance) {
            this.artifact = artifact;
            this.signature = signature;
            this.sbomFormats = new HashSet<>(sbomFormats);
            this.provenance = provenance;
        }
        
        public static ArtifactMetadata parse(String metadata) {
            if (metadata == null || metadata.trim().isEmpty()) {
                return new ArtifactMetadata(null, null, Set.of(), null);
            }
            
            String artifact = null;
            String signature = null;
            Set<String> sbomFormats = new HashSet<>();
            String provenance = null;
            
            String[] parts = metadata.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("artifact=")) {
                    artifact = part.substring("artifact=".length());
                } else if (part.startsWith("sig=")) {
                    signature = part.substring("sig=".length());
                } else if (part.startsWith("sbom=")) {
                    String sbomPart = part.substring("sbom=".length());
                    for (String format : sbomPart.split(",")) {
                        sbomFormats.add(format.trim());
                    }
                } else if (part.startsWith("prov=")) {
                    provenance = part.substring("prov=".length());
                }
            }
            
            return new ArtifactMetadata(artifact, signature, sbomFormats, provenance);
        }
        
        public String getArtifact() { return artifact; }
        public String getSignature() { return signature; }
        public Set<String> getSbomFormats() { return new HashSet<>(sbomFormats); }
        public String getProvenance() { return provenance; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (artifact != null) sb.append("artifact=").append(artifact);
            if (signature != null) {
                if (sb.length() > 0) sb.append(";");
                sb.append("sig=").append(signature);
            }
            if (!sbomFormats.isEmpty()) {
                if (sb.length() > 0) sb.append(";");
                sb.append("sbom=").append(String.join(",", sbomFormats));
            }
            if (provenance != null) {
                if (sb.length() > 0) sb.append(";");
                sb.append("prov=").append(provenance);
            }
            return sb.toString();
        }
    }
}