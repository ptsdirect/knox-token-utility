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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MavenDnsResolver
 */
class MavenDnsResolverTest {
    
    private MavenDnsResolver resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new MavenDnsResolver();
    }
    
    @Test
    void testMavenArtifactCreation() {
        MavenDnsResolver.MavenArtifact artifact = new MavenDnsResolver.MavenArtifact(
            "com.mdttee.knox", "pts", "central", "gg");
        
        assertEquals("com.mdttee.knox", artifact.getGroupId());
        assertEquals("pts", artifact.getArtifactId());
        assertEquals("central", artifact.getRepository());
        assertEquals("gg", artifact.getSignature());
    }
    
    @Test
    void testMavenArtifactWithNullValues() {
        MavenDnsResolver.MavenArtifact artifact = new MavenDnsResolver.MavenArtifact(
            "com.example", "test", null, null);
        
        assertEquals("com.example", artifact.getGroupId());
        assertEquals("test", artifact.getArtifactId());
        assertNull(artifact.getRepository());
        assertNull(artifact.getSignature());
    }
    
    @Test
    void testMavenArtifactEqualsAndHashCode() {
        MavenDnsResolver.MavenArtifact artifact1 = new MavenDnsResolver.MavenArtifact(
            "com.mdttee.knox", "pts", "central", "gg");
        MavenDnsResolver.MavenArtifact artifact2 = new MavenDnsResolver.MavenArtifact(
            "com.mdttee.knox", "pts", "central", "gg");
        MavenDnsResolver.MavenArtifact artifact3 = new MavenDnsResolver.MavenArtifact(
            "com.different", "pts", "central", "gg");
        
        assertEquals(artifact1, artifact2);
        assertEquals(artifact1.hashCode(), artifact2.hashCode());
        assertNotEquals(artifact1, artifact3);
        assertNotEquals(artifact1.hashCode(), artifact3.hashCode());
        
        assertNotEquals(artifact1, null);
        assertNotEquals(artifact1, "not an artifact");
        assertEquals(artifact1, artifact1);
    }
    
    @Test
    void testMavenArtifactToString() {
        MavenDnsResolver.MavenArtifact artifact = new MavenDnsResolver.MavenArtifact(
            "com.mdttee.knox", "pts", "central", "gg");
        String toString = artifact.toString();
        
        assertTrue(toString.contains("com.mdttee.knox"));
        assertTrue(toString.contains("pts"));
        assertTrue(toString.contains("central"));
        assertTrue(toString.contains("gg"));
    }
    
    @Test
    void testResolveMavenArtifactWithInvalidHostname() {
        // Test with invalid hostname that should fail DNS resolution
        MavenDnsResolver.MavenArtifact artifact = resolver.resolveMavenArtifact("invalid.nonexistent.example.test");
        assertNull(artifact);
    }
    
    @Test
    void testResolveMavenArtifactWithNullHostname() {
        // Test with null hostname - should return null gracefully rather than throw
        MavenDnsResolver.MavenArtifact artifact = resolver.resolveMavenArtifact(null);
        assertNull(artifact);
    }
    
    @Test
    void testResolveMavenArtifactWithEmptyHostname() {
        // Test with empty hostname
        MavenDnsResolver.MavenArtifact artifact = resolver.resolveMavenArtifact("");
        assertNull(artifact);
    }
    
    /**
     * Test the parsing logic by creating a test utility class that exposes the private method.
     * This tests the core parsing functionality without complex DNS mocking.
     */
    @Test
    void testTxtRecordParsing() {
        TestMavenDnsResolver testResolver = new TestMavenDnsResolver();
        
        // Test valid record
        String txtRecord = "groupid=com.mdttee.knox;artifactid=pts;repo=central;sig=gg";
        MavenDnsResolver.MavenArtifact artifact = testResolver.parseMavenArtifactPublic(txtRecord);
        
        assertNotNull(artifact);
        assertEquals("com.mdttee.knox", artifact.getGroupId());
        assertEquals("pts", artifact.getArtifactId());
        assertEquals("central", artifact.getRepository());
        assertEquals("gg", artifact.getSignature());
    }
    
    @Test
    void testTxtRecordParsingWithMinimalFields() {
        TestMavenDnsResolver testResolver = new TestMavenDnsResolver();
        
        // Test with only required fields
        String txtRecord = "groupid=com.example;artifactid=test";
        MavenDnsResolver.MavenArtifact artifact = testResolver.parseMavenArtifactPublic(txtRecord);
        
        assertNotNull(artifact);
        assertEquals("com.example", artifact.getGroupId());
        assertEquals("test", artifact.getArtifactId());
        assertNull(artifact.getRepository());
        assertNull(artifact.getSignature());
    }
    
    @Test
    void testTxtRecordParsingWithMissingGroupId() {
        TestMavenDnsResolver testResolver = new TestMavenDnsResolver();
        
        // Test missing groupId
        String txtRecord = "artifactid=pts;repo=central;sig=gg";
        MavenDnsResolver.MavenArtifact artifact = testResolver.parseMavenArtifactPublic(txtRecord);
        
        assertNull(artifact);
    }
    
    @Test
    void testTxtRecordParsingWithMissingArtifactId() {
        TestMavenDnsResolver testResolver = new TestMavenDnsResolver();
        
        // Test missing artifactId
        String txtRecord = "groupid=com.mdttee.knox;repo=central;sig=gg";
        MavenDnsResolver.MavenArtifact artifact = testResolver.parseMavenArtifactPublic(txtRecord);
        
        assertNull(artifact);
    }
    
    @Test
    void testTxtRecordParsingWithEmptyString() {
        TestMavenDnsResolver testResolver = new TestMavenDnsResolver();
        
        // Test empty string
        MavenDnsResolver.MavenArtifact artifact = testResolver.parseMavenArtifactPublic("");
        assertNull(artifact);
        
        // Test null
        artifact = testResolver.parseMavenArtifactPublic(null);
        assertNull(artifact);
    }
    
    @Test
    void testTxtRecordParsingWithSpaces() {
        TestMavenDnsResolver testResolver = new TestMavenDnsResolver();
        
        // Test with extra spaces
        String txtRecord = " groupid = com.mdttee.knox ; artifactid = pts ; repo = central ; sig = gg ";
        MavenDnsResolver.MavenArtifact artifact = testResolver.parseMavenArtifactPublic(txtRecord);
        
        assertNotNull(artifact);
        assertEquals("com.mdttee.knox", artifact.getGroupId());
        assertEquals("pts", artifact.getArtifactId());
        assertEquals("central", artifact.getRepository());
        assertEquals("gg", artifact.getSignature());
    }
    
    /**
     * Test utility class that exposes the private parseMavenArtifact method for testing.
     */
    private static class TestMavenDnsResolver extends MavenDnsResolver {
        public MavenArtifact parseMavenArtifactPublic(String txtRecord) {
            try {
                java.lang.reflect.Method method = MavenDnsResolver.class.getDeclaredMethod("parseMavenArtifact", String.class);
                method.setAccessible(true);
                return (MavenArtifact) method.invoke(this, txtRecord);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke parseMavenArtifact", e);
            }
        }
    }
}