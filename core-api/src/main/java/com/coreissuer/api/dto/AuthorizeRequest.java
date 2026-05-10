package com.coreissuer.api.dto;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Pattern: Builder
 * Immutable DTO to capture the authorization payload.
 */
@Data
@Builder
public class AuthorizeRequest {
    
    @NotBlank
    private String cardId;
    
    @NotBlank
    private String merchantId;
    
    private String mcc;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
}
