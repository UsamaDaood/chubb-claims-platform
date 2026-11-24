package com.chubb.claims.events;

import com.chubb.claims.domain.EventType;
import com.chubb.claims.domain.Market;

import java.time.Instant;
import java.util.Map;

/**
 * The event contract shared by producers and consumers. Carries enough context
 * (claimant, officer, market) that consumers never need to call back into the claims service.
 */
public record ClaimDomainEvent(
        String eventId,
        EventType type,
        Long claimId,
        String claimNumber,
        String claimantId,
        String assignedOfficerId,
        Market market,
        String status,
        Instant occurredAt,
        Map<String, Object> data) {
}
