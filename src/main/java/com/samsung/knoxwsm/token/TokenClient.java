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

import com.samsung.knoxwsm.token.KnoxTokenUtility;
import com.samsung.knoxwsm.token.KnoxAuthClient;
import com.samsung.knoxwsm.token.MavenDnsResolver;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TokenClient {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        try {
            // Check for command line arguments
            if (args.length > 0) {
                if ("--resolve-maven".equals(args[0]) && args.length > 1) {
                    resolveMavenFromDns(args[1]);
                    return;
                } else if ("--help".equals(args[0])) {
                    printUsage();
                    return;
                }
            }
            
            // Demonstrate DNS TXT record resolution for Maven artifacts
            System.out.println("=== Maven DNS Resolver Demo ===\n");
            MavenDnsResolver dnsResolver = new MavenDnsResolver();
            
            // Example of resolving pts._maven TXT record
            String mavenHostname = "pts._maven.example.com"; // This would be the actual hostname
            System.out.printf("Attempting to resolve Maven artifact from DNS TXT record: %s\n", mavenHostname);
            
            MavenDnsResolver.MavenArtifact artifact = dnsResolver.resolveMavenArtifact(mavenHostname);
            if (artifact != null) {
                System.out.printf("Successfully resolved Maven artifact:\n");
                System.out.printf("  Group ID: %s\n", artifact.getGroupId());
                System.out.printf("  Artifact ID: %s\n", artifact.getArtifactId());
                System.out.printf("  Repository: %s\n", artifact.getRepository());
                System.out.printf("  Signature: %s\n", artifact.getSignature());
            } else {
                System.out.printf("Could not resolve Maven artifact from DNS (expected for demo)\n");
            }
            
            System.out.println("\n=== Knox Guard Token Utility Demo ===\n");
            
            // First, let's generate a test key pair if it doesn't exist
            Path privateKeyPath = Paths.get("private_key.pem");
            Path publicKeyPath = Paths.get("public_key.pem");

            if (!Files.exists(privateKeyPath) || !Files.exists(publicKeyPath)) {
                generateKeyPair(privateKeyPath, publicKeyPath);
            }

            // Read the private key file
            try (InputStream privateKeyStream = new FileInputStream(privateKeyPath.toFile())) {
                // Generate the JWT for Knox Guard enrollment
                String clientId = "5089824242"; // Knox Guard client ID
                String deviceId = "356544761873907"; // S21 IMEI
                String jwt = KnoxTokenUtility.generateSignedClientIdentifierJWT(
                    privateKeyStream,
                    clientId,
                    null // No password needed for PEM format
                );

                System.out.println("\n=== Generated JWT ===\n");
                System.out.println(jwt);

                // Read and encode the public key
                String publicKey = KnoxTokenUtility.getPublicKeyFromPrivateKey(
                    new FileInputStream(privateKeyPath.toFile())
                );

                System.out.println("\n=== Base64 Encoded Public Key ===\n");
                System.out.println(publicKey);                // Get access token
                KnoxAuthClient authClient = new KnoxAuthClient();
                Map<String, Object> tokenResponse = authClient.requestAccessToken(
                    publicKey,
                    jwt,
                    30
                );

                String accessToken = (String) tokenResponse.get("accessToken");
                System.out.println("\n=== Access Token ===\n");
                System.out.println(accessToken);

                // Enroll device in Knox Guard
                Map<String, Object> enrollmentResponse = authClient.enrollDeviceInKnoxGuard(
                    accessToken,
                    "356544761873907",  // S21 IMEI
                    "5089824242"        // Knox Guard client ID
                );

                System.out.println("\n=== Enrollment Response ===\n");
                System.out.println(mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(enrollmentResponse));
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Resolves Maven artifact information from DNS TXT record.
     * Usage: java -jar knox-token-utility.jar --resolve-maven pts._maven.example.com
     */
    public static void resolveMavenFromDns(String hostname) {
        System.out.printf("Resolving Maven artifact from DNS TXT record: %s\n", hostname);
        
        MavenDnsResolver resolver = new MavenDnsResolver();
        MavenDnsResolver.MavenArtifact artifact = resolver.resolveMavenArtifact(hostname);
        
        if (artifact != null) {
            System.out.println("Maven artifact resolved successfully:");
            System.out.printf("  Group ID: %s\n", artifact.getGroupId());
            System.out.printf("  Artifact ID: %s\n", artifact.getArtifactId());
            System.out.printf("  Repository: %s\n", artifact.getRepository() != null ? artifact.getRepository() : "N/A");
            System.out.printf("  Signature: %s\n", artifact.getSignature() != null ? artifact.getSignature() : "N/A");
            
            // Example of how this could be used to construct Maven coordinates
            String mavenCoordinates = String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
            System.out.printf("  Maven coordinates: %s\n", mavenCoordinates);
        } else {
            System.err.println("Failed to resolve Maven artifact from DNS TXT record");
            System.exit(1);
        }
    }
    
    /**
     * Prints usage information for the Knox Token Utility.
     */
    public static void printUsage() {
        System.out.println("Knox Token Utility - Usage:");
        System.out.println();
        System.out.println("  Default mode (no arguments):");
        System.out.println("    java -jar knox-token-utility.jar");
        System.out.println("    Runs the full Knox Guard token demo");
        System.out.println();
        System.out.println("  Resolve Maven artifact from DNS TXT record:");
        System.out.println("    java -jar knox-token-utility.jar --resolve-maven <hostname>");
        System.out.println("    Example: java -jar knox-token-utility.jar --resolve-maven pts._maven.example.com");
        System.out.println();
        System.out.println("  Help:");
        System.out.println("    java -jar knox-token-utility.jar --help");
        System.out.println();
        System.out.println("  DNS TXT Record Format:");
        System.out.println("    groupid=com.mdttee.knox;artifactid=pts;repo=central;sig=gg");
        System.out.println();
    }

    public static void generateKeyPair(Path privateKeyPath, Path publicKeyPath) throws Exception {
        // Generate EC key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256); // P-256 curve for ES256
        KeyPair pair = keyGen.generateKeyPair();

        // Save private key in PKCS#8 format with PEM header/footer
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";
        Files.writeString(privateKeyPath, privateKeyPem);

        // Save public key in X.509 format with PEM header/footer
        // This format is compatible with Knox Guard's requirements
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----\n";
        Files.writeString(publicKeyPath, publicKeyPem);
    }
}
