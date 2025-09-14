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

import java.io.IOException;

/**
 * Exception representing an HTTP error returned by the Knox Cloud Services API.
 * Provides status code, raw error body (if any) and a user friendly suggestion.
 */
public class KnoxApiException extends IOException {
    private final int statusCode;
    private final String errorBody;
    private final String suggestion;

    public KnoxApiException(int statusCode, String errorBody, String suggestion) {
        super("Request failed with code " + statusCode + (errorBody != null ? (": " + errorBody) : ""));
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.suggestion = suggestion;
    }

    public int getStatusCode() { return statusCode; }
    public String getErrorBody() { return errorBody; }
    public String getSuggestion() { return suggestion; }
}
