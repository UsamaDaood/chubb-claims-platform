package com.chubb.claims.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Read model built by the event consumer. The (user_id, event_id) unique constraint makes the
 * consumer idempotent under at-least-once delivery.
 */
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_user_event", columnNames = {"user_id", "event_id"}),
        indexes = @Index(name = "ix_notification_user", columnList = "user_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    private Long claimId;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(String userId, String eventId, Long claimId, String message, Instant createdAt) {
        this.userId = userId;
        this.eventId = eventId;
        this.claimId = claimId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public Long getClaimId() { return claimId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
