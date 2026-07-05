package com.coreissuer.api.idempotency;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pattern: Cache / Idempotency Cache
 * Data Structure: ConcurrentHashMap for lock-free reads and atomic putIfAbsent.
 *
 * Entries expire (completed after 24h, in-flight markers after 5m) so the map
 * cannot grow without bound and a crashed request cannot wedge its key forever.
 * The durable source of truth is the idempotency_record table.
 */
@Component
public class IdempotencyCache {

    private static final Duration COMPLETED_TTL = Duration.ofHours(24);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final long SWEEP_INTERVAL_MS = 60_000;

    private final ConcurrentHashMap<String, CachedIdempotentResponse> cache = new ConcurrentHashMap<>();

    public CachedIdempotentResponse get(String key) {
        return cache.get(key);
    }

    public void putResponse(String key, String requestHash, String responseBody) {
        cache.put(key, new CachedIdempotentResponse(requestHash, responseBody, Instant.now()));
    }

    /** @return true if this caller won the right to process the key. */
    public boolean markProcessing(String key, String requestHash) {
        return cache.putIfAbsent(key, new CachedIdempotentResponse(requestHash, null, Instant.now())) == null;
    }

    /** Releases an in-flight marker without disturbing a completed entry. */
    public void removeIfProcessing(String key) {
        cache.computeIfPresent(key, (k, v) -> v.isProcessing() ? null : v);
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    public void evictExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> {
            Duration ttl = entry.getValue().isProcessing() ? PROCESSING_TTL : COMPLETED_TTL;
            return entry.getValue().getCachedAt().plus(ttl).isBefore(now);
        });
    }
}
