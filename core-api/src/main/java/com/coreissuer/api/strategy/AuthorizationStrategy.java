package com.coreissuer.api.strategy;

import java.math.BigDecimal;

/**
 * Pattern: Strategy
 * Selected by TransactionService (or a resolver) based on currency and merchant country.
 */
public interface AuthorizationStrategy {
    
    /**
     * Determines if the strategy applies to this transaction.
     */
    boolean supports(String currency, String merchantCountry);
    
    /**
     * Computes any fees associated with this transaction.
     */
    BigDecimal computeFee(BigDecimal amount);
    
    /**
     * Optional rules check specific to the strategy.
     * @return null if pass, or a decline reason string if failed.
     */
    String checkRules(BigDecimal amount);
}
