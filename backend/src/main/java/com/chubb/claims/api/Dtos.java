package com.chubb.claims.api;

import com.chubb.claims.domain.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The REST contract. The backend owns it (published as OpenAPI at /v3/api-docs); the Angular
 * client mirrors these shapes. Entities are never returned directly.
 */
public final class Dtos {
    private Dtos() {
    }

    // ---- requests
    public record LoginRequest(@NotBlank String userId) {}

    public record SubmitClaimRequest(
            @NotNull ClaimType type,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @Size(max = 200) String incidentLocation,
            @NotNull @DecimalMin("0.01") BigDecimal estimatedAmount) {}

    public record AssessmentRequest(@NotNull @DecimalMin("0.00") BigDecimal reserveAmount,
                                    @Size(max = 1000) String notes) {}

    public record InfoRequestCreate(@NotBlank @Size(max = 1000) String question) {}

    public record InfoResponse(@NotBlank @Size(max = 2000) String answer) {}

    public record ApproveRequest(@NotNull @DecimalMin("0.01") BigDecimal approvedAmount,
                                 @Size(max = 1000) String reason) {}

    public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record SettleRequest(@Size(max = 100) String paymentReference) {}

    // ---- responses
    public record UserView(String id, String name, Role role, Market market) {}

    public record LoginResponse(String token, UserView user) {}

    public record ClaimSummary(
            Long id, String claimNumber, Market market, ClaimType type, ClaimStatus status,
            String currency, BigDecimal estimatedAmount,
            BigDecimal reserveAmount,   // staff only; null for claimants
            BigDecimal approvedAmount,
            String claimantId, String claimantName, String assignedOfficerId, String assignedOfficerName,
            Instant submittedAt, Instant updatedAt, long ageDays, Instant slaDueAt, boolean slaBreached) {}

    public record ActivityView(EventType type, String actorName, String message, Instant at, boolean internal) {}

    public record InfoRequestView(Long id, String question, Instant requestedAt, String answer, Instant answeredAt) {}

    public record ClaimDetail(
            ClaimSummary claim, String description, LocalDate incidentDate, String incidentLocation,
            String decisionReason, String paymentReference,
            List<ActivityView> activity, List<InfoRequestView> infoRequests,
            /** Server-computed so the UI never re-implements the state machine / authorisation rules. */
            List<String> allowedActions) {}

    public record NotificationView(Long id, Long claimId, String message, Instant createdAt, boolean read) {}

    // ---- dashboards
    public record OfficerSummary(long inReview, long awaitingClaimant, long approvedAwaitingSettlement,
                                 long slaBreached, long dueWithin24h, long unassignedInMarket,
                                 long decidedLast30Days) {}

    public record CurrencyTotal(String currency, long openCount, BigDecimal amount) {}

    public record ExposureReport(List<ExposureRow> rows, List<CurrencyTotal> totals,
                                 long unassignedCount, long slaBreachedCount, long unreservedCount) {}

    public record WorkloadRow(String officerId, String officerName, Market market,
                              long inReview, long awaitingClaimant, long approvedAwaitingSettlement,
                              long slaBreached, long oldestAgeDays, long decidedLast30Days,
                              double avgDecisionDays) {}
}
