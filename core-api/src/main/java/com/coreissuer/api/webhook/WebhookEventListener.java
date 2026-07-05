package com.coreissuer.api.webhook;

import com.coreissuer.api.event.TransactionEvent;
import com.coreissuer.common.domain.WebhookDelivery;
import com.coreissuer.common.domain.WebhookStatus;
import com.coreissuer.common.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventListener.class);

    private final WebhookDeliveryRepository webhookDeliveryRepository;

    @Value("${coreissuer.webhook.target-url:}")
    private String targetUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransactionEvent(TransactionEvent event) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            log.debug("No webhook target configured; skipping delivery for txn {}", event.getTransactionId());
            return;
        }

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEventType(event.getEventType());
        delivery.setPayload(event.getPayload());
        delivery.setTargetUrl(targetUrl);
        delivery.setStatus(WebhookStatus.PENDING.name());
        webhookDeliveryRepository.save(delivery);
    }
}
