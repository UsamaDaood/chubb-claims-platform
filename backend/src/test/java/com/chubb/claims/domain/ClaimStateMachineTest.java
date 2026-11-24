package com.chubb.claims.domain;

import com.chubb.claims.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Pure domain tests: no Spring, no database. */
class ClaimStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private Claim newClaim() {
        return Claim.submit("c-1", Market.SG, ClaimType.MOTOR, "dent", LocalDate.of(2025, 12, 31), "SG",
                new BigDecimal("1000"), null, NOW);
    }

    @Test
    void happyPathToSettlement() {
        Claim c = newClaim();
        assertEquals(ClaimStatus.SUBMITTED, c.getStatus());
        c.pickUp("o-1", NOW);
        c.requestInfo(NOW);
        c.resumeReview(NOW);
        c.approve(new BigDecimal("900"), "ok", NOW);
        c.settle("PAY-1", NOW);
        assertEquals(ClaimStatus.SETTLED, c.getStatus());
        assertEquals(new BigDecimal("900"), c.getApprovedAmount());
    }

    @Test
    void cannotPickUpTwice() {
        Claim c = newClaim();
        c.pickUp("o-1", NOW);
        assertThrows(ApiException.class, () -> c.pickUp("o-2", NOW));
    }

    @Test
    void cannotApproveBeforeReview() {
        Claim c = newClaim();
        assertThrows(ApiException.class, () -> c.approve(BigDecimal.TEN, "x", NOW));
    }

    @Test
    void terminalStatesAreFinal() {
        Claim c = newClaim();
        c.pickUp("o-1", NOW);
        c.reject("excluded", NOW);
        assertThrows(ApiException.class, () -> c.approve(BigDecimal.TEN, "x", NOW));
        assertThrows(ApiException.class, () -> c.settle(null, NOW));
        for (ClaimStatus s : ClaimStatus.values()) {
            assertFalse(ClaimStatus.REJECTED.canMoveTo(s));
            assertFalse(ClaimStatus.SETTLED.canMoveTo(s));
        }
    }

    @Test
    void onlyOpenClaimsCarryExposure() {
        assertTrue(ClaimStatus.APPROVED.carriesExposure());
        assertFalse(ClaimStatus.SETTLED.carriesExposure());
        assertFalse(ClaimStatus.REJECTED.carriesExposure());
    }

    @Test
    void claimNumberIsDerivedFromMarket() {
        Claim c = newClaim();
        // id is null until persisted; the number format is exercised end-to-end in ClaimLifecycleTest
        assertNull(c.getClaimNumber());
    }
}
