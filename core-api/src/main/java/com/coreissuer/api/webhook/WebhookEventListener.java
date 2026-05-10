package com.coreissuer.api.webhook;

import com.coreissuer.api.event.TransactionEvent;
import com.coreissuer.common.domain.WebhookDelivery;
import com.coreissuer.common.domain.WebhookStatus;
import com.coreissuer.common.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookDeliveryRepository webhookDeliveryRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransactionEvent(TransactionEvent event) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEventType(event.getEventType());
        delivery.setPayload(event.getPayload());
        
        // Hardcoded dummy target URL for demonstration
        delivery.setTargetUrl("https://webhook.site/dummy-merchant-endpoint");
        
        delivery.setStatus(WebhookStatus.PENDING.name());
        webhookDeliveryRepository.save(delivery);
    }
}
