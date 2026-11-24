package com.chubb.claims.service;

import com.chubb.claims.api.Dtos.*;
import com.chubb.claims.common.ApiException;
import com.chubb.claims.domain.*;
import com.chubb.claims.events.OutboxService;
import com.chubb.claims.repo.*;
import com.chubb.claims.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Claim use-cases. Every mutating method is one transaction that (1) loads the aggregate,
 * (2) checks authorisation, (3) applies the domain change, (4) appends to the audit trail and
 * (5) records a domain event in the outbox. Nothing here talks to Kafka directly.
 */
@Service
public class ClaimService {

    private static final List<ClaimStatus> OFFICER_ACTIVE =
            List.of(ClaimStatus.IN_REVIEW, ClaimStatus.INFO_REQUESTED, ClaimStatus.APPROVED);

    private final ClaimRepository claims;
    private final ActivityRepository activities;
    private final InfoRequestRepository infoRequests;
    private final UserRepository users;
    private final OutboxService outbox;
    private final Clock clock;
    private final long slaDays;
    private final BigDecimal officerLimit;

    public ClaimService(ClaimRepository claims, ActivityRepository activities, InfoRequestRepository infoRequests,
                        UserRepository users, OutboxService outbox, Clock clock,
                        @Value("${claims.sla-days}") long slaDays,
                        @Value("${claims.officer-approval-limit}") BigDecimal officerLimit) {
        this.claims = claims;
        this.activities = activities;
        this.infoRequests = infoRequests;
        this.users = users;
        this.outbox = outbox;
        this.clock = clock;
        this.slaDays = slaDays;
        this.officerLimit = officerLimit;
    }

    // ------------------------------------------------------------------ claimant use-cases

    @Transactional
    public ClaimDetail submit(AuthUser actor, SubmitClaimRequest req, String idempotencyKey) {
        actor.require(Role.CLAIMANT);
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;
        if (key != null) {
            Optional<Claim> existing = claims.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                // Safe retry of the same submission: return the original rather than creating a duplicate.
                if (!existing.get().getClaimantId().equals(actor.id())) {
                    throw ApiException.conflict("Idempotency key already used");
                }
                return detail(actor, existing.get());
            }
        }
        if (actor.market() == null) {
            throw ApiException.badRequest("Claimant has no home market");
        }
        Instant now = clock.instant();
        Claim c = claims.save(Claim.submit(actor.id(), actor.market(), req.type(), req.description(),
                req.incidentDate(), req.incidentLocation(), req.estimatedAmount(), key, now));
        c.assignNumber();
        log(c, EventType.CLAIM_SUBMITTED, actor, "Claim submitted", false);
        outbox.record(EventType.CLAIM_SUBMITTED, c, Map.of("estimatedAmount", c.getEstimatedAmount(),
                "type", c.getType().name()));
        return detail(actor, c);
    }

    @Transactional
    public ClaimDetail provideInfo(AuthUser actor, Long claimId, Long requestId, String answer) {
        actor.require(Role.CLAIMANT);
        Claim c = loadVisible(actor, claimId);
        if (c.getStatus() != ClaimStatus.INFO_REQUESTED) {
            throw ApiException.conflict("No information is currently being requested on this claim");
        }
        InfoRequest r = infoRequests.findById(requestId)
                .filter(x -> x.getClaimId().equals(claimId))
                .orElseThrow(() -> ApiException.notFound("Information request not found"));
        if (r.isAnswered()) {
            throw ApiException.conflict("This request has already been answered");
        }
        Instant now = clock.instant();
        r.answer(answer, now);
        log(c, EventType.INFO_PROVIDED, actor, "Claimant answered: " + r.getQuestion(), false);
        if (infoRequests.countByClaimIdAndAnsweredAtIsNull(claimId) == 0) {
            c.resumeReview(now);   // all questions answered: back on the officer's desk
        }
        outbox.record(EventType.INFO_PROVIDED, c, Map.of("requestId", requestId));
        return detail(actor, c);
    }

    @Transactional(readOnly = true)
    public List<ClaimSummary> mine(AuthUser actor) {
        actor.require(Role.CLAIMANT);
        Map<String, String> names = names();
        return claims.findByClaimantIdOrderBySubmittedAtDesc(actor.id()).stream()
                .map(c -> summary(actor, c, names)).toList();
    }

    // ------------------------------------------------------------------ staff use-cases

    /** Unassigned claims in the officer's market (all markets for managers), oldest first. */
    @Transactional(readOnly = true)
    public List<ClaimSummary> queue(AuthUser actor, ClaimType type) {
        actor.require(Role.OFFICER, Role.MANAGER);
        Map<String, String> names = names();
        return claims.findByStatusAndAssignedOfficerIdIsNullOrderBySubmittedAtAsc(ClaimStatus.SUBMITTED).stream()
                .filter(c -> actor.role() == Role.MANAGER || c.getMarket() == actor.market())
                .filter(c -> type == null || c.getType() == type)
                .map(c -> summary(actor, c, names)).toList();
    }

    /** The officer's own desk: everything assigned to them that still needs action. */
    @Transactional(readOnly = true)
    public List<ClaimSummary> assigned(AuthUser actor) {
        actor.require(Role.OFFICER);
        Map<String, String> names = names();
        return claims.findByAssignedOfficerIdAndStatusInOrderBySubmittedAtAsc(actor.id(), OFFICER_ACTIVE).stream()
                .map(c -> summary(actor, c, names)).toList();
    }

    @Transactional(readOnly = true)
    public ClaimDetail get(AuthUser actor, Long id) {
        return detail(actor, loadVisible(actor, id));
    }

    @Transactional
    public ClaimDetail assign(AuthUser actor, Long id) {
        actor.require(Role.OFFICER);
        Claim c = loadVisible(actor, id);
        // @Version guards the race: if another officer commits first, our flush fails -> 409.
        c.pickUp(actor.id(), clock.instant());
        log(c, EventType.CLAIM_ASSIGNED, actor, "Picked up by " + actor.name(), false);
        outbox.record(EventType.CLAIM_ASSIGNED, c, Map.of());
        return detail(actor, c);
    }

    @Transactional
    public ClaimDetail assess(AuthUser actor, Long id, AssessmentRequest req) {
        Claim c = loadVisible(actor, id);
        assertHandler(actor, c);
        c.assess(req.reserveAmount(), clock.instant());
        String note = "Reserve set to %s %s".formatted(c.getCurrency(), req.reserveAmount().toPlainString())
                + (req.notes() == null || req.notes().isBlank() ? "" : ": " + req.notes());
        log(c, EventType.CLAIM_ASSESSED, actor, note, true);   // internal: hidden from the claimant
        outbox.record(EventType.CLAIM_ASSESSED, c, Map.of("reserveAmount", req.reserveAmount()));
        return detail(actor, c);
    }

    @Transactional
    public ClaimDetail requestInfo(AuthUser actor, Long id, String question) {
        Claim c = loadVisible(actor, id);
        assertHandler(actor, c);
        c.requestInfo(clock.instant());
        InfoRequest r = infoRequests.save(new InfoRequest(id, question, actor.id(), clock.instant()));
        log(c, EventType.INFO_REQUESTED, actor, "Information requested: " + question, false);
        outbox.record(EventType.INFO_REQUESTED, c, Map.of("requestId", r.getId()));
        return detail(actor, c);
    }

    @Transactional
    public ClaimDetail approve(AuthUser actor, Long id, ApproveRequest req) {
        Claim c = loadVisible(actor, id);
        assertHandler(actor, c);
        if (actor.role() == Role.OFFICER && req.approvedAmount().compareTo(officerLimit) > 0) {
            throw ApiException.forbidden("Amount exceeds your approval authority (%s); escalate to a manager"
                    .formatted(officerLimit.toPlainString()));
        }
        c.approve(req.approvedAmount(), req.reason(), clock.instant());
        log(c, EventType.CLAIM_APPROVED, actor,
                "Approved for %s %s".formatted(c.getCurrency(), req.approvedAmount().toPlainString()), false);
        outbox.record(EventType.CLAIM_APPROVED, c, Map.of("approvedAmount", req.approvedAmount()));
        return detail(actor, c);
    }

    @Transactional
    public ClaimDetail reject(AuthUser actor, Long id, String reason) {
        Claim c = loadVisible(actor, id);
        assertHandler(actor, c);
        c.reject(reason, clock.instant());
        log(c, EventType.CLAIM_REJECTED, actor, "Rejected: " + reason, false);
        outbox.record(EventType.CLAIM_REJECTED, c, Map.of());
        return detail(actor, c);
    }

    @Transactional
    public ClaimDetail settle(AuthUser actor, Long id, String paymentReference) {
        Claim c = loadVisible(actor, id);
        assertHandler(actor, c);
        c.settle(paymentReference, clock.instant());
        log(c, EventType.CLAIM_SETTLED, actor, "Settled"
                + (paymentReference == null || paymentReference.isBlank() ? "" : " (ref " + paymentReference + ")"), false);
        outbox.record(EventType.CLAIM_SETTLED, c, Map.of());
        return detail(actor, c);
    }

    // ------------------------------------------------------------------ authorisation

    /** Claimants only ever see their own claims (404, not 403, so ids can't be probed); officers only their market. */
    private Claim loadVisible(AuthUser actor, Long id) {
        Claim c = claims.findById(id).orElseThrow(() -> ApiException.notFound("Claim not found"));
        switch (actor.role()) {
            case CLAIMANT -> {
                if (!c.getClaimantId().equals(actor.id())) {
                    throw ApiException.notFound("Claim not found");
                }
            }
            case OFFICER -> {
                if (c.getMarket() != actor.market()) {
                    throw ApiException.forbidden("Claim belongs to another market");
                }
            }
            case MANAGER -> { /* all markets */ }
        }
        return c;
    }

    private boolean isHandler(AuthUser actor, Claim c) {
        return actor.role() == Role.MANAGER
                || (actor.role() == Role.OFFICER && actor.id().equals(c.getAssignedOfficerId()));
    }

    private void assertHandler(AuthUser actor, Claim c) {
        if (!isHandler(actor, c)) {
            throw ApiException.forbidden("Only the assigned officer or a manager can do this");
        }
    }

    private List<String> allowedActions(AuthUser actor, Claim c) {
        List<String> out = new ArrayList<>();
        ClaimStatus s = c.getStatus();
        switch (actor.role()) {
            case CLAIMANT -> {
                if (s == ClaimStatus.INFO_REQUESTED) out.add("PROVIDE_INFO");
            }
            case OFFICER, MANAGER -> {
                if (actor.role() == Role.OFFICER && s == ClaimStatus.SUBMITTED) out.add("ASSIGN");
                if (isHandler(actor, c) && s == ClaimStatus.IN_REVIEW) {
                    out.addAll(List.of("ASSESS", "REQUEST_INFO", "APPROVE", "REJECT"));
                }
                if (isHandler(actor, c) && s == ClaimStatus.APPROVED) out.add("SETTLE");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ mapping

    private void log(Claim c, EventType type, AuthUser actor, String message, boolean internal) {
        activities.save(new ClaimActivity(c.getId(), type, actor.id(), actor.name(), message, internal, clock.instant()));
    }

    private Map<String, String> names() {
        return users.findAll().stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    }

    ClaimSummary summary(AuthUser viewer, Claim c, Map<String, String> names) {
        Instant now = clock.instant();
        Instant due = c.getSubmittedAt().plus(slaDays, ChronoUnit.DAYS);
        boolean breached = c.getStatus().awaitingDecision() && now.isAfter(due);
        boolean staff = viewer.isStaff();
        return new ClaimSummary(c.getId(), c.getClaimNumber(), c.getMarket(), c.getType(), c.getStatus(),
                c.getCurrency(), c.getEstimatedAmount(),
                staff ? c.getReserveAmount() : null,
                c.getApprovedAmount(),
                c.getClaimantId(), names.get(c.getClaimantId()),
                c.getAssignedOfficerId(), c.getAssignedOfficerId() == null ? null : names.get(c.getAssignedOfficerId()),
                c.getSubmittedAt(), c.getUpdatedAt(),
                Duration.between(c.getSubmittedAt(), now).toDays(), due, breached);
    }

    private ClaimDetail detail(AuthUser viewer, Claim c) {
        boolean staff = viewer.isStaff();
        List<ActivityView> timeline = activities.findByClaimIdOrderByOccurredAtAsc(c.getId()).stream()
                .filter(a -> staff || !a.isInternal())
                .map(a -> new ActivityView(a.getType(), a.getActorName(), a.getMessage(), a.getOccurredAt(), a.isInternal()))
                .toList();
        List<InfoRequestView> requests = infoRequests.findByClaimIdOrderByIdAsc(c.getId()).stream()
                .map(r -> new InfoRequestView(r.getId(), r.getQuestion(), r.getRequestedAt(), r.getAnswer(), r.getAnsweredAt()))
                .toList();
        return new ClaimDetail(summary(viewer, c, names()), c.getDescription(), c.getIncidentDate(),
                c.getIncidentLocation(), c.getDecisionReason(), c.getPaymentReference(),
                timeline, requests, allowedActions(viewer, c));
    }
}
