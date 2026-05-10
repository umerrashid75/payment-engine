package com.coreissuer.api.idempotency;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Pattern: Cache / Idempotency Cache
 * Data Structure: ConcurrentHashMap for lock-free reads and atomic putIfAbsent.
 */
@Component
public class IdempotencyCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String get(String key) {
        return cache.get(key);
    }

    public void put(String key, String responseBody) {
        cache.put(key, responseBody);
    }
    
    public boolean putIfAbsent(String key, String placeholder) {
        return cache.putIfAbsent(key, placeholder) == null;
    }
    
    public void remove(String key) {
        cache.remove(key);
    }
}
