package com.chubb.claims.events;

import jakarta.persistence.*;

import java.time.Instant;

/** Transactional outbox row: written in the same DB transaction as the state change it describes. */
@Entity
@Table(name = "outbox_message", indexes = @Index(name = "ix_outbox_unpublished", columnList = "published_at,id"))
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    /** Kafka message key; keeps all events of one claim on one partition, so they stay ordered. */
    @Column(name = "message_key", nullable = false)
    private String key;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxMessage() {
    }

    public OutboxMessage(String eventId, String key, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.key = key;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getKey() { return key; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
