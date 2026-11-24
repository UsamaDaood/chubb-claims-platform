package com.chubb.claims.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** A question the officer needs answered by the claimant before the claim can proceed. */
@Entity
@Table(name = "info_request", indexes = @Index(name = "ix_inforeq_claim", columnList = "claim_id"))
public class InfoRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(nullable = false, length = 1000)
    private String question;

    private String requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(length = 2000)
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    protected InfoRequest() {
    }

    public InfoRequest(Long claimId, String question, String requestedBy, Instant requestedAt) {
        this.claimId = claimId;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public void answer(String answer, Instant now) {
        this.answer = answer;
        this.answeredAt = now;
    }

    public boolean isAnswered() { return answeredAt != null; }
    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public String getQuestion() { return question; }
    public String getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getAnswer() { return answer; }
    public Instant getAnsweredAt() { return answeredAt; }
}
