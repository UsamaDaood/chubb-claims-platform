package com.chubb.claims.service;

import com.chubb.claims.api.Dtos.*;
import com.chubb.claims.domain.*;
import com.chubb.claims.repo.ClaimRepository;
import com.chubb.claims.repo.UserRepository;
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

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<ClaimStatus> AWAITING_DECISION = List.of(ClaimStatus.SUBMITTED, ClaimStatus.IN_REVIEW);

    private final ClaimRepository claims;
    private final UserRepository users;
    private final Clock clock;
    private final long slaDays;

    public DashboardService(ClaimRepository claims, UserRepository users, Clock clock,
                            @Value("${claims.sla-days}") long slaDays) {
        this.claims = claims;
        this.users = users;
        this.clock = clock;
        this.slaDays = slaDays;
    }

    /**
     * Outstanding liability exposure: what we would owe if every open claim were paid at the best
     * available estimate. Grouped (never summed) across currencies - no FX assumptions are made.
     */
    public ExposureReport exposure(AuthUser actor) {
        actor.require(Role.MANAGER);
        List<ExposureRow> rows = claims.exposure(ClaimStatus.exposed());
        Map<String, List<ExposureRow>> byCurrency = rows.stream().collect(Collectors.groupingBy(ExposureRow::currency));
        List<CurrencyTotal> totals = byCurrency.entrySet().stream()
                .map(e -> new CurrencyTotal(e.getKey(),
                        e.getValue().stream().mapToLong(ExposureRow::count).sum(),
                        e.getValue().stream().map(ExposureRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(CurrencyTotal::currency))
                .toList();
        Instant cutoff = clock.instant().minus(slaDays, ChronoUnit.DAYS);
        return new ExposureReport(rows, totals,
                claims.countByStatus(ClaimStatus.SUBMITTED),
                claims.countByStatusInAndSubmittedAtBefore(AWAITING_DECISION, cutoff),
                claims.countByStatusInAndReserveAmountIsNull(ClaimStatus.exposed()));
    }

    /** Team view for managers: one row per officer. */
    public List<WorkloadRow> workload(AuthUser actor) {
        actor.require(Role.MANAGER);
        Instant now = clock.instant();
        Map<String, List<Claim>> byOfficer = claims.findByAssignedOfficerIdIsNotNull().stream()
                .collect(Collectors.groupingBy(Claim::getAssignedOfficerId));
        return users.findByRole(Role.OFFICER).stream()
                .map(o -> workloadRow(o, byOfficer.getOrDefault(o.getId(), List.of()), now))
                .sorted(Comparator.comparing(WorkloadRow::market).thenComparing(WorkloadRow::officerName))
                .toList();
    }

    /** Officer's personal summary: what needs attention on my desk right now. */
    public OfficerSummary officerSummary(AuthUser actor) {
        actor.require(Role.OFFICER);
        Instant now = clock.instant();
        List<Claim> mine = claims.findByAssignedOfficerIdIsNotNull().stream()
                .filter(c -> actor.id().equals(c.getAssignedOfficerId())).toList();
        Counts k = counts(mine, now);
        return new OfficerSummary(k.inReview, k.awaiting, k.approved, k.breached, k.dueSoon,
                claims.countByStatusAndMarket(ClaimStatus.SUBMITTED, actor.market()), k.decided30);
    }

    private WorkloadRow workloadRow(AppUser o, List<Claim> mine, Instant now) {
        Counts k = counts(mine, now);
        long oldest = mine.stream().filter(c -> c.getStatus().carriesExposure())
                .mapToLong(c -> Duration.between(c.getSubmittedAt(), now).toDays()).max().orElse(0);
        double avg = mine.stream().filter(c -> c.getDecidedAt() != null && c.getDecidedAt().isAfter(now.minus(30, ChronoUnit.DAYS)))
                .mapToDouble(c -> Duration.between(c.getSubmittedAt(), c.getDecidedAt()).toHours() / 24.0)
                .average().orElse(0);
        return new WorkloadRow(o.getId(), o.getName(), o.getMarket(), k.inReview, k.awaiting, k.approved,
                k.breached, oldest, k.decided30, Math.round(avg * 10) / 10.0);
    }

    private static final class Counts {
        long inReview, awaiting, approved, breached, dueSoon, decided30;
    }

    private Counts counts(List<Claim> mine, Instant now) {
        Counts k = new Counts();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        for (Claim c : mine) {
            switch (c.getStatus()) {
                case IN_REVIEW -> k.inReview++;
                case INFO_REQUESTED -> k.awaiting++;
                case APPROVED -> k.approved++;
                default -> { }
            }
            if (c.getStatus().awaitingDecision()) {
                Instant due = c.getSubmittedAt().plus(slaDays, ChronoUnit.DAYS);
                if (now.isAfter(due)) k.breached++;
                else if (now.plus(24, ChronoUnit.HOURS).isAfter(due)) k.dueSoon++;
            }
            if (c.getDecidedAt() != null && c.getDecidedAt().isAfter(thirtyDaysAgo)) k.decided30++;
        }
        return k;
    }
}
