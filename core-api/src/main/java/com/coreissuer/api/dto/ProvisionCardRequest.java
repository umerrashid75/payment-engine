package com.coreissuer.api.dto;

import com.coreissuer.common.domain.CardTier;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ProvisionCardRequest {
    @NotNull
    private CardTier tier;
    
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal initialBalance;
    
    @NotNull
    private String currency;
}
