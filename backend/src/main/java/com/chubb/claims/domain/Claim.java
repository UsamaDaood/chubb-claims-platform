package com.chubb.claims.domain;

import com.chubb.claims.common.ApiException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Aggregate root. All state changes go through intent-revealing methods so the lifecycle
 * rules live in one place. {@code @Version} gives optimistic locking: two officers racing to
 * pick up the same claim cannot both win.
 */
@Entity
@Table(name = "claim", indexes = {
        @Index(name = "ix_claim_status_officer", columnList = "status,assigned_officer_id"),
        @Index(name = "ix_claim_claimant", columnList = "claimant_id"),
        @Index(name = "ix_claim_market_status", columnList = "market,status")
})
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", unique = true)
    private String claimNumber;

    @Column(name = "claimant_id", nullable = false)
    private String claimantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "market", nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false)
    private ClaimType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Column(nullable = false, length = 2000)
    private String description;

    private LocalDate incidentDate;
    private String incidentLocation;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal estimatedAmount;
    @Column(precision = 19, scale = 2)
    private BigDecimal reserveAmount;
    @Column(precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "assigned_officer_id")
    private String assignedOfficerId;

    @Column(length = 1000)
    private String decisionReason;
    private String paymentReference;

    @Column(nullable = false)
    private Instant submittedAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant assignedAt;
    private Instant decidedAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Version
    private long version;

    protected Claim() {
    }

    public static Claim submit(String claimantId, Market market, ClaimType type, String description,
                               LocalDate incidentDate, String incidentLocation, BigDecimal estimatedAmount,
                               String idempotencyKey, Instant now) {
        Claim c = new Claim();
        c.claimantId = claimantId;
        c.market = market;
        c.type = type;
        c.status = ClaimStatus.SUBMITTED;
        c.description = description;
        c.incidentDate = incidentDate;
        c.incidentLocation = incidentLocation;
        c.currency = market.currency();
        c.estimatedAmount = estimatedAmount;
        c.idempotencyKey = idempotencyKey;
        c.submittedAt = now;
        c.updatedAt = now;
        return c;
    }

    /** Human-friendly number derived from the generated id. Call once after the first save. */
    public void assignNumber() {
        this.claimNumber = "CLM-%s-%06d".formatted(market, id);
    }

    public void pickUp(String officerId, Instant now) {
        if (assignedOfficerId != null) {
            throw ApiException.conflict("Claim is already assigned");
        }
        moveTo(ClaimStatus.IN_REVIEW, now);
        this.assignedOfficerId = officerId;
        this.assignedAt = now;
    }

    public void assess(BigDecimal reserve, Instant now) {
        requireStatus(ClaimStatus.IN_REVIEW);
        this.reserveAmount = reserve;
        this.updatedAt = now;
    }

    public void requestInfo(Instant now) {
        moveTo(ClaimStatus.INFO_REQUESTED, now);
    }

    public void resumeReview(Instant now) {
        moveTo(ClaimStatus.IN_REVIEW, now);
    }

    public void approve(BigDecimal amount, String reason, Instant now) {
        moveTo(ClaimStatus.APPROVED, now);
        this.approvedAmount = amount;
        this.decisionReason = reason;
        this.decidedAt = now;
    }

    public void reject(String reason, Instant now) {
        moveTo(ClaimStatus.REJECTED, now);
        this.decisionReason = reason;
        this.decidedAt = now;
    }

    public void settle(String paymentReference, Instant now) {
        moveTo(ClaimStatus.SETTLED, now);
        this.paymentReference = paymentReference;
    }

    /** Seed/demo helper only. */
    public void backdate(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    private void requireStatus(ClaimStatus expected) {
        if (status != expected) {
            throw ApiException.conflict("Claim is %s, expected %s".formatted(status, expected));
        }
    }

    private void moveTo(ClaimStatus target, Instant now) {
        if (!status.canMoveTo(target)) {
            throw ApiException.conflict("Claim cannot move from %s to %s".formatted(status, target));
        }
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public String getClaimantId() { return claimantId; }
    public Market getMarket() { return market; }
    public ClaimType getType() { return type; }
    public ClaimStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getIncidentLocation() { return incidentLocation; }
    public String getCurrency() { return currency; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public BigDecimal getReserveAmount() { return reserveAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public String getAssignedOfficerId() { return assignedOfficerId; }
    public String getDecisionReason() { return decisionReason; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public long getVersion() { return version; }
}
