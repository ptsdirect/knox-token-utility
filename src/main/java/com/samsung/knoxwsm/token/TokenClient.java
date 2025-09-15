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
                System.out.println(publicKey);                // Get access token from primary domain
                KnoxAuthClient authClient = new KnoxAuthClient();
                Map<String, Object> tokenResponse = authClient.requestAccessToken(
                    publicKey,
                    jwt,
                    30
                );

                String accessToken = (String) tokenResponse.get("accessToken");
                System.out.println("\n=== Access Token (Primary Domain) ===\n");
                System.out.println(accessToken);

                // Demonstrate secondary trust domain usage
                try {
                    Map<String, Object> secondaryTokenResponse = authClient.requestAccessTokenFromSecondaryDomain(
                        publicKey,
                        jwt,
                        30
                    );
                    String secondaryAccessToken = (String) secondaryTokenResponse.get("accessToken");
                    System.out.println("\n=== Access Token (Secondary Domain - trust.mdttee.com) ===\n");
                    System.out.println(secondaryAccessToken);
                } catch (Exception e) {
                    System.out.println("\n=== Secondary Domain Not Available (Expected in Demo) ===");
                    System.out.println("Secondary domain (trust.mdttee.com) not reachable: " + e.getMessage());
                }

                // Show trust domain configuration
                TrustDomainConfig trustConfig = authClient.getTrustConfig();
                System.out.println("\n=== Trust Domain Configuration ===");
                System.out.println("Primary Domain: " + trustConfig.getPrimaryTrustDomain());
                System.out.println("Secondary Domain: " + trustConfig.getSecondaryTrustDomain());
                System.out.println("Artifact Metadata: " + trustConfig.getArtifactMetadata());
                
                // Parse and display artifact metadata
                TrustDomainConfig.ArtifactMetadata metadata = trustConfig.parseArtifactMetadata();
                System.out.println("\n=== Parsed Artifact Metadata ===");
                System.out.println("Artifact: " + metadata.getArtifact());
                System.out.println("Signature: " + metadata.getSignature());
                System.out.println("SBOM Formats: " + metadata.getSbomFormats());
                System.out.println("Provenance: " + metadata.getProvenance());

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
