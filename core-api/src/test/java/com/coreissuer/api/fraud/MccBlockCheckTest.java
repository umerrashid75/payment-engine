package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MccBlockCheckTest {

    private final MccBlockCheck check = new MccBlockCheck();

    private AuthorizeRequest requestWithMcc(String mcc) {
        return AuthorizeRequest.builder()
                .cardId("c1").merchantId("m1")
                .mcc(mcc).amount(new BigDecimal("10.00")).currency("USD")
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7995", "6012"})
    @DisplayName("blocks restricted MCCs")
    void blockedMcc_blocks(String mcc) {
        FraudResult result = check.check(requestWithMcc(mcc));
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).isEqualTo("RESTRICTED_MCC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"5411", "4111", "5812"})
    @DisplayName("passes allowed MCCs")
    void allowedMcc_passes(String mcc) {
        FraudResult result = check.check(requestWithMcc(mcc));
        assertThat(result.isPass()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("passes when MCC is null")
    @ValueSource(strings = {""})
    void nullMcc_passes() {
        AuthorizeRequest req = AuthorizeRequest.builder()
                .cardId("c1").merchantId("m1").mcc(null)
                .amount(new BigDecimal("10.00")).currency("USD")
                .build();
        assertThat(check.check(req).isPass()).isTrue();
    }
}
