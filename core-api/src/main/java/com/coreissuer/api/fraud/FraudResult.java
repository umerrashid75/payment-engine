package com.coreissuer.api.fraud;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FraudResult {
    private final boolean pass;
    private final String reason;

    public static FraudResult pass() {
        return new FraudResult(true, null);
    }

    public static FraudResult block(String reason) {
        return new FraudResult(false, reason);
    }
}
