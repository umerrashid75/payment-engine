package com.coreissuer.api.webhook;

import com.coreissuer.common.domain.WebhookDelivery;
import com.coreissuer.common.domain.WebhookStatus;
import com.coreissuer.common.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WebhookScheduler {

    private final WebhookDeliveryRepository repository;
    private final HttpWebhookAdapter adapter;

    private static final int MAX_ATTEMPTS = 6;
    private static final long[] BACKOFF_SECONDS = {0, 60, 300, 1800, 3600, 7200}; // 0s, 1m, 5m, 30m, 1h, 2h

    @Scheduled(fixedDelay = 60000) // run every minute
    public void processPendingWebhooks() {
        List<WebhookDelivery> pendingDeliveries = repository.findByStatus(WebhookStatus.PENDING.name());

        LocalDateTime now = LocalDateTime.now();

        for (WebhookDelivery delivery : pendingDeliveries) {
            if (delivery.getNextAttemptAt() != null && delivery.getNextAttemptAt().isAfter(now)) {
                continue; // Not ready for retry yet
            }

            try {
                adapter.deliver(delivery.getTargetUrl(), delivery.getPayload());
                delivery.setStatus(WebhookStatus.SUCCEEDED.name());
            } catch (Exception e) {
                delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                delivery.setLastError(e.getMessage());

                if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
                    delivery.setStatus(WebhookStatus.FAILED.name());
                } else {
                    long backoff = BACKOFF_SECONDS[delivery.getAttemptCount()];
                    delivery.setNextAttemptAt(now.plusSeconds(backoff));
                }
            }
            repository.save(delivery);
        }
    }
}
