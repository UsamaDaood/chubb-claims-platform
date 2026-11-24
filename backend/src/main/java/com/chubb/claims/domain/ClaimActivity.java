package com.chubb.claims.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Append-only audit trail / timeline. Internal entries (e.g. reserve changes) are hidden from claimants. */
@Entity
@Table(name = "claim_activity", indexes = @Index(name = "ix_activity_claim", columnList = "claim_id"))
public class ClaimActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType type;

    private String actorId;
    private String actorName;

    @Column(length = 2500)
    private String message;

    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ClaimActivity() {
    }

    public ClaimActivity(Long claimId, EventType type, String actorId, String actorName, String message,
                         boolean internal, Instant occurredAt) {
        this.claimId = claimId;
        this.type = type;
        this.actorId = actorId;
        this.actorName = actorName;
        this.message = message;
        this.internal = internal;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public EventType getType() { return type; }
    public String getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getMessage() { return message; }
    public boolean isInternal() { return internal; }
    public Instant getOccurredAt() { return occurredAt; }
}
