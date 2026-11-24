package com.chubb.claims.domain;

/** Domain event types. Also used as the activity-log entry type. */
public enum EventType {
    CLAIM_SUBMITTED, CLAIM_ASSIGNED, CLAIM_ASSESSED, INFO_REQUESTED, INFO_PROVIDED,
    CLAIM_APPROVED, CLAIM_REJECTED, CLAIM_SETTLED
}
