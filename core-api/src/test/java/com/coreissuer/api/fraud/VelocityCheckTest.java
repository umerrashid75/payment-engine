package com.coreissuer.api.fraud;

import com.coreissuer.api.dto.AuthorizeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VelocityCheckTest {

    private VelocityCheck check;

    @BeforeEach
    void setUp() {
        check = new VelocityCheck(new VelocityWindow());
    }

    private AuthorizeRequest request(String cardId) {
        return AuthorizeRequest.builder()
                .cardId(cardId).merchantId("m1").mcc("5411")
                .amount(new BigDecimal("10.00")).currency("USD")
                .build();
    }

    @Test
    @DisplayName("first 3 requests on a card pass")
    void underThreshold_passes() {
        for (int i = 0; i < 3; i++) {
            assertThat(check.check(request("card-1")).isPass()).isTrue();
        }
    }

    @Test
    @DisplayName("4th request on same card in the same window is blocked")
    void overThreshold_blocks() {
        for (int i = 0; i < 3; i++) {
            check.check(request("card-2"));
        }
        FraudResult result = check.check(request("card-2"));
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).isEqualTo("VELOCITY_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("velocity windows are isolated per card")
    void differentCards_isolatedWindows() {
        for (int i = 0; i < 3; i++) {
            check.check(request("card-a"));
        }
        // card-b has not been seen yet — should pass
        assertThat(check.check(request("card-b")).isPass()).isTrue();
    }
}
