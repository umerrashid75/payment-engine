package com.coreissuer.api.strategy;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Pattern: Strategy
 */
@Component
public class InternationalAuthStrategy implements AuthorizationStrategy {

    @Override
    public boolean supports(String currency, String merchantCountry) {
        // Applies to every merchant outside the home country
        return !"US".equalsIgnoreCase(merchantCountry);
    }

    @Override
    public BigDecimal computeFee(BigDecimal amount) {
        // 2.5% international transaction fee
        return amount.multiply(new BigDecimal("0.025"));
    }

    @Override
    public String checkRules(BigDecimal amount) {
        return null; // Pass
    }
}
