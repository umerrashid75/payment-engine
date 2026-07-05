package com.coreissuer.api.dto;

import com.coreissuer.common.domain.CardTier;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;

@Data
public class ProvisionCardRequest {
    @NotNull
    private CardTier tier;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal initialBalance;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 currency code")
    private String currency;
}
