package com.chubb.claims.repo;

import com.chubb.claims.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByIdempotencyKey(String idempotencyKey);

    List<Claim> findByClaimantIdOrderBySubmittedAtDesc(String claimantId);

    /** The pick-up queue: unassigned claims, oldest first (closest to SLA breach). */
    List<Claim> findByStatusAndAssignedOfficerIdIsNullOrderBySubmittedAtAsc(ClaimStatus status);

    List<Claim> findByAssignedOfficerIdAndStatusInOrderBySubmittedAtAsc(String officerId, Collection<ClaimStatus> statuses);

    List<Claim> findByAssignedOfficerIdIsNotNull();

    long countByStatusAndMarket(ClaimStatus status, Market market);

    long countByStatus(ClaimStatus status);

    long countByStatusInAndSubmittedAtBefore(Collection<ClaimStatus> statuses, Instant cutoff);

    long countByStatusInAndReserveAmountIsNull(Collection<ClaimStatus> statuses);

    /**
     * Outstanding liability exposure, aggregated in the database. Best-estimate per claim:
     * approved amount if decided, else the officer's reserve, else the claimant's own estimate.
     */
    @Query("""
            select new com.chubb.claims.domain.ExposureRow(
                c.market, c.type, c.status, c.currency, count(c),
                sum(coalesce(c.approvedAmount, c.reserveAmount, c.estimatedAmount)))
            from Claim c
            where c.status in :statuses
            group by c.market, c.type, c.status, c.currency
            """)
    List<ExposureRow> exposure(@Param("statuses") Collection<ClaimStatus> statuses);
}
