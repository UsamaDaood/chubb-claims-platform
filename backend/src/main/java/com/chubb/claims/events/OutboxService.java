package com.chubb.claims.events;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.EventType;
import com.chubb.claims.repo.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxService(OutboxRepository outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Joins the caller's transaction: the event is persisted atomically with the claim change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(EventType type, Claim claim, Map<String, Object> data) {
        ClaimDomainEvent event = new ClaimDomainEvent(
                UUID.randomUUID().toString(), type, claim.getId(), claim.getClaimNumber(),
                claim.getClaimantId(), claim.getAssignedOfficerId(), claim.getMarket(),
                claim.getStatus().name(), clock.instant(), data);
        try {
            outbox.save(new OutboxMessage(event.eventId(), String.valueOf(claim.getId()),
                    mapper.writeValueAsString(event), clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + type, e);
        }
    }

    @Transactional
    public void markPublished(Long id) {
        outbox.findById(id).ifPresent(m -> m.markPublished(clock.instant()));
    }
}
