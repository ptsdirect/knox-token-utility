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
 * Model for a certificate.json structure used by Knox Guard. The JSON schema isn't formally published
 * in code; we accept flexible field names (camelCase or snake_case) when parsing via {@link KnoxCertificateParser}.
 */
public record KnoxCertificate(
    String clientIdentifier,
    String client_id,
    String clientId,
    String publicKey,
    String public_key,
    String privateKey,
    String private_key
) {
    public String resolvedClientId() {
        if (clientIdentifier != null && !clientIdentifier.isBlank()) return clientIdentifier;
        if (clientId != null && !clientId.isBlank()) return clientId;
        if (client_id != null && !client_id.isBlank()) return client_id;
        return null;
    }

    public String resolvedPublicKey() {
        if (publicKey != null && !publicKey.isBlank()) return publicKey;
        if (public_key != null && !public_key.isBlank()) return public_key;
        return null;
    }

    public String resolvedPrivateKey() {
        if (privateKey != null && !privateKey.isBlank()) return privateKey;
        if (private_key != null && !private_key.isBlank()) return private_key;
        return null;
    }
}
