package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
public class VelocityCheck implements FraudCheck {

    private final VelocityWindow velocityWindow;
    private static final int MAX_TRANSACTIONS_PER_MINUTE = 3;

    @Override
    public FraudResult check(AuthorizeRequest request) {
        int count = velocityWindow.recordAndCount(request.getCardId());
        if (count > MAX_TRANSACTIONS_PER_MINUTE) {
            return FraudResult.block("VELOCITY_LIMIT_EXCEEDED");
        }
        return FraudResult.pass();
    }
}
