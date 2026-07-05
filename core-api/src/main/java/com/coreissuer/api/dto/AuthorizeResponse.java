package com.coreissuer.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AuthorizeResponse {
    private String transactionId;
    private String status;
    private BigDecimal amount;
    private BigDecimal feeAmount;
    private String currency;
    private BigDecimal availableBalanceAfter;
    private String declineReason;
}
