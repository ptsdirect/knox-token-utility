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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.InputStream;
import java.util.Date;

/**
 * @deprecated Consolidated into {@link KnoxTokenUtility}. Retained as a thin delegating facade
 * to preserve backwards compatibility during migration. Will be removed in a future minor version.
 */
@Deprecated
public final class KnoxCertificateJwtUtility {
    private static final long TEN_YEARS_IN_MILLIS = 315_532_800_000L; // ~10 years

    private KnoxCertificateJwtUtility() {}

    public static String generateSignedClientIdentifierJWTWithIdpAccessToken(InputStream certificateJson,
                                                                             String clientIdentifier,
                                                                             String idpAccessToken) {
        KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
        long now = System.currentTimeMillis();
        String atHash = (idpAccessToken == null ? null : Integer.toHexString(idpAccessToken.hashCode()));
        return Jwts.builder()
            .header().add("typ", "JWT").and()
            .subject(clientIdentifier)
            .audience().add("PTSDIRECT.ORG").and()
            .claim("cdt", now / 1000)
            .claim("scope", "all")
            .claim("idp_at", atHash)
            .issuedAt(new Date(now))
            .expiration(new Date(now + TEN_YEARS_IN_MILLIS))
            .signWith(cert.privateKey(), SignatureAlgorithm.ES256)
            .compact();
    }

    public static String generateSignedSessionTokenJWT(InputStream certificateJson, String sessionToken) {
        KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .header().add("typ", "JWT").and()
            .subject("session")
            .audience().add("PTSDIRECT.ORG").and()
            .claim("st", sessionToken)
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3_600_000L))
            .signWith(cert.privateKey(), SignatureAlgorithm.ES256)
            .compact();
    }

    public static String generateSignedAccessTokenJWT(InputStream certificateJson, String accessToken) {
        KnoxCertificateParser.ParsedCertificate cert = KnoxCertificateParser.parse(certificateJson);
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .header().add("typ", "JWT").and()
            .subject("access")
            .audience().add("PTSDIRECT.ORG").and()
            .claim("at", accessToken)
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3_600_000L))
            .signWith(cert.privateKey(), SignatureAlgorithm.ES256)
            .compact();
    }
}
