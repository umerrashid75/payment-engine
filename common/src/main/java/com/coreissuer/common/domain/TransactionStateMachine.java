package com.coreissuer.common.domain;

import java.util.EnumMap;
import java.util.Set;

/**
 * Data Structure: EnumMap allowed transitions.
 * State machine for Transaction lifecycle.
 */
public class TransactionStateMachine {

    private static final EnumMap<TransactionStatus, Set<TransactionStatus>> TRANSITIONS = new EnumMap<>(TransactionStatus.class);

    static {
        TRANSITIONS.put(TransactionStatus.AUTHORIZED, Set.of(TransactionStatus.CAPTURED, TransactionStatus.REVERSED));
        TRANSITIONS.put(TransactionStatus.CAPTURED, Set.of(TransactionStatus.REFUNDED));
        TRANSITIONS.put(TransactionStatus.REVERSED, Set.of());
        TRANSITIONS.put(TransactionStatus.REFUNDED, Set.of());
        TRANSITIONS.put(TransactionStatus.DECLINED, Set.of());
    }

    public static void validateTransition(TransactionStatus current, TransactionStatus next) {
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid transaction state transition from " + current + " to " + next);
        }
    }
}
