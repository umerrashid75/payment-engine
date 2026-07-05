package com.coreissuer.api.dto;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Pattern: Builder
 * Immutable DTO to capture the authorization payload.
 */
@Data
@Builder
public class AuthorizeRequest {

    @NotBlank
    @Size(max = 36)
    private String cardId;

    @NotBlank
    @Size(max = 64)
    private String merchantId;

    /** ISO-3166 alpha-2. Defaults to US when omitted. */
    @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO-3166 alpha-2 country code")
    private String merchantCountry;

    @Pattern(regexp = "\\d{4}", message = "must be a 4-digit MCC")
    private String mcc;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 currency code")
    private String currency;
}
