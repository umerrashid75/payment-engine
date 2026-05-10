package com.coreissuer.api.fraud;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Structure: ArrayDeque acts as a fast LinkedList for the sliding window.
 */
@Component
public class VelocityWindow {

    private final Map<String, ArrayDeque<Instant>> windows = new ConcurrentHashMap<>();
    private static final int WINDOW_SECONDS = 60;
    
    public int recordAndCount(String cardId) {
        ArrayDeque<Instant> window = windows.computeIfAbsent(cardId, k -> new ArrayDeque<>());
        
        synchronized (window) {
            Instant now = Instant.now();
            Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
            
            // Discard entries older than 60s
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            
            window.addLast(now);
            return window.size();
        }
    }
}
