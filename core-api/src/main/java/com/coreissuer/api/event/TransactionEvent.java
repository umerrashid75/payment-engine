package com.coreissuer.api.event;

import org.springframework.context.ApplicationEvent;

public class TransactionEvent extends ApplicationEvent {

    private final String transactionId;
    private final String eventType;
    private final String payload; // Serialized JSON payload

    public TransactionEvent(Object source, String transactionId, String eventType, String payload) {
        super(source);
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}
