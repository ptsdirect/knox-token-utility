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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * DNS TXT record resolver for Maven artifact discovery.
 * Resolves TXT records with format: "groupid=com.mdttee.knox;artifactid=pts;repo=central;sig=gg"
 */
public class MavenDnsResolver {
    private static final Logger logger = LoggerFactory.getLogger(MavenDnsResolver.class);
    
    /**
     * Represents Maven artifact information from DNS TXT record.
     */
    public static class MavenArtifact {
        private final String groupId;
        private final String artifactId;
        private final String repository;
        private final String signature;
        
        public MavenArtifact(String groupId, String artifactId, String repository, String signature) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.repository = repository;
            this.signature = signature;
        }
        
        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getRepository() { return repository; }
        public String getSignature() { return signature; }
        
        @Override
        public String toString() {
            return String.format("MavenArtifact{groupId='%s', artifactId='%s', repository='%s', signature='%s'}", 
                groupId, artifactId, repository, signature);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MavenArtifact that = (MavenArtifact) o;
            return java.util.Objects.equals(groupId, that.groupId) &&
                   java.util.Objects.equals(artifactId, that.artifactId) &&
                   java.util.Objects.equals(repository, that.repository) &&
                   java.util.Objects.equals(signature, that.signature);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(groupId, artifactId, repository, signature);
        }
    }
    
    /**
     * Resolves Maven artifact information from DNS TXT record.
     * 
     * @param hostname the hostname to query (e.g., "pts._maven.example.com")
     * @return MavenArtifact containing parsed information, or null if not found or parse error
     */
    public MavenArtifact resolveMavenArtifact(String hostname) {
        try {
            String txtRecord = queryTxtRecord(hostname);
            if (txtRecord == null) {
                logger.warn("No TXT record found for hostname: {}", hostname);
                return null;
            }
            
            return parseMavenArtifact(txtRecord);
        } catch (Exception e) {
            logger.error("Failed to resolve Maven artifact for hostname: {}", hostname, e);
            return null;
        }
    }
    
    /**
     * Queries DNS TXT record for the given hostname.
     * 
     * @param hostname the hostname to query
     * @return the TXT record content, or null if not found
     * @throws NamingException if DNS query fails
     */
    private String queryTxtRecord(String hostname) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        
        DirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(hostname, new String[]{"TXT"});
            Attribute txtAttr = attrs.get("TXT");
            
            if (txtAttr != null && txtAttr.size() > 0) {
                String txtRecord = (String) txtAttr.get(0);
                // Remove quotes if present
                if (txtRecord.startsWith("\"") && txtRecord.endsWith("\"")) {
                    txtRecord = txtRecord.substring(1, txtRecord.length() - 1);
                }
                logger.debug("Found TXT record for {}: {}", hostname, txtRecord);
                return txtRecord;
            }
            
            return null;
        } finally {
            ctx.close();
        }
    }
    
    /**
     * Parses Maven artifact information from TXT record content.
     * Expected format: "groupid=com.mdttee.knox;artifactid=pts;repo=central;sig=gg"
     * 
     * @param txtRecord the TXT record content
     * @return parsed MavenArtifact, or null if parsing fails
     */
    private MavenArtifact parseMavenArtifact(String txtRecord) {
        if (txtRecord == null || txtRecord.trim().isEmpty()) {
            logger.warn("Empty TXT record content");
            return null;
        }
        
        Map<String, String> params = new HashMap<>();
        
        // Split by semicolon and parse key=value pairs
        String[] parts = txtRecord.split(";");
        for (String part : parts) {
            part = part.trim();
            int equalPos = part.indexOf('=');
            if (equalPos > 0 && equalPos < part.length() - 1) {
                String key = part.substring(0, equalPos).trim().toLowerCase();
                String value = part.substring(equalPos + 1).trim();
                params.put(key, value);
            }
        }
        
        // Extract required fields
        String groupId = params.get("groupid");
        String artifactId = params.get("artifactid");
        String repository = params.get("repo");
        String signature = params.get("sig");
        
        if (groupId == null || artifactId == null) {
            logger.warn("Missing required fields in TXT record. groupId: {}, artifactId: {}", groupId, artifactId);
            return null;
        }
        
        logger.debug("Parsed Maven artifact: groupId={}, artifactId={}, repository={}, signature={}", 
                     groupId, artifactId, repository, signature);
        
        return new MavenArtifact(groupId, artifactId, repository, signature);
    }
}