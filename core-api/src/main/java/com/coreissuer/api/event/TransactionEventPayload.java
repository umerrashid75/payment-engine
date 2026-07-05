package com.coreissuer.api.event;

import com.coreissuer.common.domain.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Webhook-safe view of a transaction. Never serialize the JPA entity itself:
 * it drags in the Card (PAN/CVV hashes) and Account (balances) graphs, which
 * must not leave the system.
 */
@Data
@Builder
public class TransactionEventPayload {

    private String transactionId;
    private String status;
    private String merchantId;
    private String mcc;
    private BigDecimal amount;
    private BigDecimal feeAmount;
    private String currency;
    private String declineReason;
    private String createdAt;

    public static TransactionEventPayload from(Transaction tx) {
        return TransactionEventPayload.builder()
                .transactionId(tx.getId())
                .status(tx.getStatus())
                .merchantId(tx.getMerchantId())
                .mcc(tx.getMcc())
                .amount(tx.getAmount())
                .feeAmount(tx.getFeeAmount())
                .currency(tx.getCurrency())
                .declineReason(tx.getDeclineReason())
                .createdAt(tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null)
                .build();
    }
}
