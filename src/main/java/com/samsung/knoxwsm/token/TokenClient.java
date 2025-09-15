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

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenClient {
    private static final Logger log = LoggerFactory.getLogger(TokenClient.class);

    public static void main(String[] args) {
        try {
            Map<String, String> cli = parseArgs(args);
            if (cli.containsKey("help") || cli.isEmpty()) { printHelp(); return; }

            String mode = cli.getOrDefault("mode", "full-flow");
            String clientId = firstNonBlank(cli.get("client-id"), Config.get("KNOX_GUARD_CLIENT_ID", "5089824242"));
            String deviceImei = firstNonBlank(cli.get("device-imei"), Config.get("DEVICE_IMEI", "356544761873907"));
            int tokenValidity = Integer.parseInt(firstNonBlank(cli.get("validity"), Config.get("ACCESS_TOKEN_VALIDITY_MINUTES", "30")));
            Path privateKeyPath = Paths.get(firstNonBlank(cli.get("private-key"), Config.get("PRIVATE_KEY_PATH", "private_key.pem")));
            Path publicKeyPath = Paths.get(firstNonBlank(cli.get("public-key"), Config.get("PUBLIC_KEY_PATH", "public_key.pem")));
            Path certificatePath = cli.containsKey("certificate") ? Paths.get(cli.get("certificate")) : null;
            boolean outputJson = cli.containsKey("output-json");
            boolean quiet = cli.containsKey("quiet");

            // Optional external tokens for new signing flows
            String idpAccessToken = cli.get("idp-access-token");
            String sessionToken = cli.get("session-token");
            String accessTokenRaw = cli.get("access-token-raw");
            String rsaPlaintext = cli.get("plaintext");
            String uploadFile = cli.get("upload-file"); // path to JSON file for upload-devices
            String uploadId = cli.get("upload-id"); // for get-upload

            if (certificatePath != null && !Files.exists(certificatePath)) {
                throw new IllegalArgumentException("certificate file not found: " + certificatePath);
            }

            if (!Files.exists(privateKeyPath) || !Files.exists(publicKeyPath)) {
                if (!quiet) System.out.println("Generating key pair (missing files)..."); // user-facing
                log.info("Generating new EC key pair at {} / {}", privateKeyPath, publicKeyPath);
                generateKeyPair(privateKeyPath, publicKeyPath);
            }

            KnoxAuthClient authClient = new KnoxAuthClient();
            String publicKey = KnoxTokenUtility.getPublicKeyBase64(publicKeyPath);

            if (mode.equals("generate-keys")) { if (!quiet) System.out.println("Key pair present at: " + privateKeyPath + " / " + publicKeyPath); log.debug("generate-keys mode completed"); return; }

            // New certificate-based modes (no network calls by themselves)
            if (mode.equals("sign-client-idp")) {
                requireCertificate(certificatePath);
                try (InputStream certStream = new FileInputStream(certificatePath.toFile())) {
                    String jwt = KnoxTokenUtility.generateSignedClientIdentifierJWTWithIdpAccessToken(certStream, clientId, idpAccessToken);
                    emit("clientIdpJwt", jwt, outputJson);
                }
                return;
            }
            if (mode.equals("sign-session")) {
                if (sessionToken == null) throw new IllegalArgumentException("--session-token required for sign-session mode");
                requireCertificate(certificatePath);
                try (InputStream certStream = new FileInputStream(certificatePath.toFile())) {
                    String jwt = KnoxTokenUtility.generateSignedSessionTokenJWT(certStream, sessionToken);
                    emit("sessionJwt", jwt, outputJson);
                }
                return;
            }
            if (mode.equals("sign-access")) {
                if (accessTokenRaw == null) throw new IllegalArgumentException("--access-token-raw required for sign-access mode");
                requireCertificate(certificatePath);
                try (InputStream certStream = new FileInputStream(certificatePath.toFile())) {
                    String jwt = KnoxTokenUtility.generateSignedAccessTokenJWT(certStream, accessTokenRaw);
                    emit("accessJwt", jwt, outputJson);
                }
                return;
            }
            if (mode.equals("encrypt")) {
                if (rsaPlaintext == null) throw new IllegalArgumentException("--plaintext required for encrypt mode");
                if (publicKey == null) throw new IllegalStateException("Public key not derivable");
                // For RSA we need certificate-provided RSA key (future extension) - reuse provided public-key path for now
                // Expect an RSA public key in PEM format at --public-key
                String pem = Files.readString(publicKeyPath);
                String encrypted = KnoxEncryptionUtility.encryptSmallPayload(pem, rsaPlaintext);
                log.debug("Encrypted payload of length {} bytes", rsaPlaintext.length());
                emit("encrypted", encrypted, outputJson);
                return;
            }

            String jwt;
            try (InputStream privateKeyStream = new FileInputStream(privateKeyPath.toFile())) {
                jwt = KnoxTokenUtility.generateSignedClientIdentifierJWT(privateKeyStream, clientId, null);
            }

            if (mode.equals("request-token") || mode.equals("full-flow") || mode.equals("enroll-guard") || mode.equals("validate-token") || mode.equals("refresh-token") || mode.equals("upload-devices") || mode.equals("list-uploads") || mode.equals("get-upload")) {
                Map<String, Object> tokenResponse = authClient.requestAccessToken(publicKey, jwt, tokenValidity);
                String accessToken = (String) tokenResponse.get("accessToken");
                if (!quiet) emit("accessToken", accessToken, outputJson);
                if (mode.equals("request-token")) return;
                if (mode.equals("validate-token")) { emitMap("validateResult", authClient.validateAccessToken(accessToken), outputJson, quiet); return; }
                if (mode.equals("enroll-guard") || mode.equals("full-flow")) { emitMap("enrollment", authClient.enrollDeviceInKnoxGuard(accessToken, deviceImei, clientId), outputJson, quiet); return; }
                if (mode.equals("upload-devices")) {
                    if (uploadFile == null) throw new IllegalArgumentException("--upload-file <path> required for upload-devices mode");
                    String json = Files.readString(Paths.get(uploadFile));
                    emitMap("uploadResult", authClient.uploadDevices(accessToken, json), outputJson, quiet);
                    return;
                }
                if (mode.equals("list-uploads")) {
                    emitMap("uploads", authClient.listDeviceUploads(accessToken), outputJson, quiet); return; }
                if (mode.equals("get-upload")) {
                    if (uploadId == null) throw new IllegalArgumentException("--upload-id <id> required for get-upload mode");
                    emitMap("upload", authClient.getUploadById(accessToken, uploadId), outputJson, quiet); return; }
            }
            log.warn("Unsupported mode requested: {}", mode);
            System.err.println("Unsupported mode: " + mode);
            printHelp();
        } catch (KnoxApiException kae) {
            log.error("Knox Guard API error status={} suggestion={} bodyLength={}", kae.getStatusCode(), kae.getSuggestion(), kae.getErrorBody()==null?0:kae.getErrorBody().length());
            System.err.println("\n[Knox Guard API Error]\nStatus: " + kae.getStatusCode());
            if (kae.getErrorBody() != null && !kae.getErrorBody().isBlank()) System.err.println("Body  : " + kae.getErrorBody());
            System.err.println("Hint  : " + kae.getSuggestion());
            if (System.getenv("DEBUG") != null) { System.err.println("\nStacktrace:"); kae.printStackTrace(); }
            System.exit(2);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.toString());
            System.err.println("Unexpected error: " + e.getMessage());
            if (System.getenv("DEBUG") != null) e.printStackTrace();
            System.exit(3);
        }
    }

    private static void requireCertificate(Path cert) {
        if (cert == null) throw new IllegalArgumentException("--certificate path is required for this mode");
        if (!Files.exists(cert)) throw new IllegalArgumentException("Certificate file not found: " + cert);
    }

    public static void generateKeyPair(Path privateKeyPath, Path publicKeyPath) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair pair = keyGen.generateKeyPair();
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(privateKeyPath, privateKeyPem, StandardCharsets.UTF_8);
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(publicKeyPath, publicKeyPem, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--help") || a.equals("-h")) { map.put("help", "true"); continue; }
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (Arrays.asList("output-json","quiet").contains(key)) { map.put(key, "true"); continue; }
                if (i + 1 < args.length) { map.put(key, args[++i]); }
            }
        }
        return map;
    }

    private static void printHelp() {
        System.out.println("Knox Guard Utility CLI\n" +
                "Usage: java -jar knox-token-utility.jar --mode <mode> [options]\n\n" +
                "Modes:\n" +
                "  generate-keys          Generate key pair if absent and exit\n" +
                "  request-token          Obtain access token and print it\n" +
                "  validate-token         Request token then validate it\n" +
                "  enroll-guard           Request token then enroll device\n" +
                "  upload-devices         Request token then upload devices batch (requires --upload-file)\n" +
                "  list-uploads           Request token then list recent device uploads\n" +
                "  get-upload             Request token then fetch a specific upload (requires --upload-id)\n" +
                "  full-flow              Keys -> JWT -> Token -> Enroll (default)\n" +
                "  sign-client-idp        Sign client identifier JWT using certificate + optional IDP token hash\n" +
                "  sign-session           Sign session token JWT using certificate\n" +
                "  sign-access            Sign access token JWT using certificate\n" +
                "  encrypt                RSA encrypt small plaintext with provided public key\n\n" +
                "Options:\n" +
                "  --client-id <id>       Knox Guard client ID (env KNOX_GUARD_CLIENT_ID)\n" +
                "  --device-imei <imei>   Device IMEI (env DEVICE_IMEI)\n" +
                "  --validity <minutes>   Access token validity (15-60) default 30\n" +
                "  --private-key <path>   Private key file (default private_key.pem)\n" +
                "  --public-key <path>    Public key file (default public_key.pem)\n" +
                "  --certificate <path>   certificate.json containing keys (for sign-* modes)\n" +
                "  --idp-access-token <t> IDP/SA access token (hashed into client identifier JWT)\n" +
                "  --session-token <t>    Raw session token string for sign-session\n" +
                "  --access-token-raw <t> Raw access token for sign-access\n" +
                "  --plaintext <data>     Plaintext to RSA encrypt (encrypt mode)\n" +
                "  --upload-file <path>   JSON file containing upload payload (upload-devices)\n" +
                "  --upload-id <id>       Upload identifier (get-upload)\n" +
                "  --output-json          Emit JSON only payload values\n" +
                "  --quiet                Suppress descriptive text\n" +
                "  --help                 Show this help\n");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) { if (v != null && !v.isBlank()) return v; }
        return null;
    }

    private static void emit(String label, String value, boolean json) {
        if (json) System.out.println('{'+"\""+label+"\":\"" + escapeJson(value) + "\"}" );
        else System.out.println("\n=== " + label + " ===\n" + value);
    }

    private static void emitMap(String label, Map<String,Object> mapData, boolean json, boolean quiet) {
        if (json) {
            System.out.println('{'+"\""+label+"\":" + toJson(mapData) + '}');
        } else if (!quiet) {
            System.out.println("\n=== " + label + " ===\n" + prettyMap(mapData));
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String)obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Object eObj : ((Map<?,?>)obj).entrySet()) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) eObj;
                if (!first) sb.append(',');
                first = false;
                sb.append("\"").append(escapeJson(String.valueOf(e.getKey()))).append("\":").append(toJson(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Object v : (Collection<?>)obj) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(v));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + escapeJson(String.valueOf(obj)) + "\"";
    }

    private static String prettyMap(Map<String,Object> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,Object> e : map.entrySet()) {
            sb.append(e.getKey()).append(':').append(' ').append(String.valueOf(e.getValue())).append('\n');
        }
        return sb.toString();
    }
}