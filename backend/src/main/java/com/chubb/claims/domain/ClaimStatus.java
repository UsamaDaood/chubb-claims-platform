package com.chubb.claims.domain;

import java.util.Arrays;
import java.util.List;

/**
 * The claim lifecycle. This enum is the single source of truth for legal transitions.
 *
 * SUBMITTED -> IN_REVIEW -> (INFO_REQUESTED <-> IN_REVIEW) -> APPROVED -> SETTLED
 *                        \-> REJECTED
 */
public enum ClaimStatus {
    SUBMITTED, IN_REVIEW, INFO_REQUESTED, APPROVED, REJECTED, SETTLED;

    public boolean canMoveTo(ClaimStatus target) {
        return switch (this) {
            case SUBMITTED -> target == IN_REVIEW;
            case IN_REVIEW -> target == INFO_REQUESTED || target == APPROVED || target == REJECTED;
            case INFO_REQUESTED -> target == IN_REVIEW;
            case APPROVED -> target == SETTLED;
            case REJECTED, SETTLED -> false;
        };
    }

    /** Open claims carry liability exposure until they are rejected or paid out. */
    public boolean carriesExposure() {
        return this != REJECTED && this != SETTLED;
    }

    /** Claims still awaiting a decision (the decision SLA applies; the clock is paused while INFO_REQUESTED). */
    public boolean awaitingDecision() {
        return this == SUBMITTED || this == IN_REVIEW;
    }

    public static List<ClaimStatus> exposed() {
        return Arrays.stream(values()).filter(ClaimStatus::carriesExposure).toList();
    }
}
