package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;

/**
 * Pattern: Chain of Responsibility
 */
public interface FraudCheck {
    FraudResult check(AuthorizeRequest request);
}
