package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Order(2)
public class MccBlockCheck implements FraudCheck {

    private static final Set<String> BLOCKED_MCCS = Set.of("7995", "6012"); // Gambling, Quasi-Cash

    @Override
    public FraudResult check(AuthorizeRequest request) {
        if (request.getMcc() != null && BLOCKED_MCCS.contains(request.getMcc())) {
            return FraudResult.block("RESTRICTED_MCC");
        }
        return FraudResult.pass();
    }
}
