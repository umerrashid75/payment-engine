package com.coreissuer.api.fraud;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Structure: ArrayDeque acts as a fast LinkedList for the sliding window.
 * Idle windows are evicted on a schedule so the per-card map cannot grow
 * without bound.
 */
@Component
public class VelocityWindow {

    private final Map<String, ArrayDeque<Instant>> windows = new ConcurrentHashMap<>();
    private static final int WINDOW_SECONDS = 60;
    private static final long SWEEP_INTERVAL_MS = 300_000;

    public int recordAndCount(String cardId) {
        while (true) {
            ArrayDeque<Instant> window = windows.computeIfAbsent(cardId, k -> new ArrayDeque<>());

            synchronized (window) {
                // Lost a race with eviction: this deque is no longer in the map.
                if (windows.get(cardId) != window) {
                    continue;
                }

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

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    public void evictIdleWindows() {
        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
        windows.entrySet().removeIf(entry -> {
            ArrayDeque<Instant> window = entry.getValue();
            synchronized (window) {
                return window.isEmpty() || window.peekLast().isBefore(cutoff);
            }
        });
    }
}
