package com.coreissuer.api.strategy;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Pattern: Strategy
 */
@Component
public class DomesticAuthStrategy implements AuthorizationStrategy {

    private static final String DEFAULT_CURRENCY = "USD";
    
    @Override
    public boolean supports(String currency, String merchantCountry) {
        // Simple logic for demonstration
        return DEFAULT_CURRENCY.equalsIgnoreCase(currency) && "US".equalsIgnoreCase(merchantCountry);
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
