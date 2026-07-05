package com.coreissuer.common.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Data Structure: EnumMap allowed transitions.
 * State machine for Transaction lifecycle.
 */
public class TransactionStateMachine {

    private static final EnumMap<TransactionStatus, Set<TransactionStatus>> TRANSITIONS = new EnumMap<>(TransactionStatus.class);
    private static final Set<TransactionStatus> NO_TRANSITIONS = EnumSet.noneOf(TransactionStatus.class);

    static {
        TRANSITIONS.put(TransactionStatus.AUTHORIZED, EnumSet.of(TransactionStatus.CAPTURED, TransactionStatus.REVERSED));
        TRANSITIONS.put(TransactionStatus.CAPTURED, EnumSet.of(TransactionStatus.REFUNDED));
        TRANSITIONS.put(TransactionStatus.REVERSED, NO_TRANSITIONS);
        TRANSITIONS.put(TransactionStatus.REFUNDED, NO_TRANSITIONS);
        TRANSITIONS.put(TransactionStatus.DECLINED, NO_TRANSITIONS);
    }

    public static void validateTransition(TransactionStatus current, TransactionStatus next) {
        if (!TRANSITIONS.getOrDefault(current, NO_TRANSITIONS).contains(next)) {
            throw new IllegalStateException("Invalid transaction state transition from " + current + " to " + next);
        }
    }
}
