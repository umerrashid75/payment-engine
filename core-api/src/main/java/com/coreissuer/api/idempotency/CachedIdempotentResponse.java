package com.coreissuer.api.idempotency;

import java.time.Instant;

/**
 * Immutable cache entry. A null responseBody means the original request is
 * still in flight ("processing" marker).
 */
public final class CachedIdempotentResponse {

    private final String requestHash;
    private final String responseBody;
    private final Instant cachedAt;

    public CachedIdempotentResponse(String requestHash, String responseBody, Instant cachedAt) {
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.cachedAt = cachedAt;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public boolean isProcessing() {
        return responseBody == null;
    }
}
