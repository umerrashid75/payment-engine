package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(1)
public class AmountCeilingCheck implements FraudCheck {

    private static final BigDecimal CEILING = new BigDecimal("10000.00");

    @Override
    public FraudResult check(AuthorizeRequest request) {
        if (request.getAmount().compareTo(CEILING) > 0) {
            return FraudResult.block("AMOUNT_CEILING_EXCEEDED");
        }
        return FraudResult.pass();
    }
}
