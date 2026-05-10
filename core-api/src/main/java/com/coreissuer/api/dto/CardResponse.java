package com.coreissuer.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CardResponse {
    private String id;
    private String accountId;
    private String panLastFour;
    private Integer expiryMonth;
    private Integer expiryYear;
    private String tier;
    private String status;
    private BigDecimal availableBalance;
    private String currency;
    private LocalDateTime createdAt;
}
