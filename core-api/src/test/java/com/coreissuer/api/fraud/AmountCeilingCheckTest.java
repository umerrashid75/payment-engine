package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountCeilingCheckTest {

    private final AmountCeilingCheck check = new AmountCeilingCheck();

    private AuthorizeRequest requestFor(String amount) {
        return AuthorizeRequest.builder()
                .cardId("c1").merchantId("m1").mcc("5411")
                .amount(new BigDecimal(amount)).currency("USD")
                .build();
    }

    @Test
    @DisplayName("passes when amount is below ceiling")
    void belowCeiling_passes() {
        FraudResult result = check.check(requestFor("9999.99"));
        assertThat(result.isPass()).isTrue();
    }

    @Test
    @DisplayName("passes when amount equals ceiling")
    void atCeiling_passes() {
        FraudResult result = check.check(requestFor("10000.00"));
        assertThat(result.isPass()).isTrue();
    }

    @Test
    @DisplayName("blocks when amount exceeds ceiling")
    void aboveCeiling_blocks() {
        FraudResult result = check.check(requestFor("10000.01"));
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).isEqualTo("AMOUNT_CEILING_EXCEEDED");
    }
}
