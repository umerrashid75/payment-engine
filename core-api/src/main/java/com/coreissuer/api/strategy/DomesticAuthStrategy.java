package com.coreissuer.api.strategy;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Pattern: Strategy
 */
@Component
public class DomesticAuthStrategy implements AuthorizationStrategy {

    private static final String HOME_COUNTRY = "US";

    @Override
    public boolean supports(String currency, String merchantCountry) {
        // Routing is by merchant country; the currency is validated against the
        // cardholder account before strategy selection.
        return HOME_COUNTRY.equalsIgnoreCase(merchantCountry);
    }

    @Override
    public BigDecimal computeFee(BigDecimal amount) {
        // No fee for domestic
        return BigDecimal.ZERO;
    }

    @Override
    public String checkRules(BigDecimal amount) {
        return null; // Pass
    }
}
