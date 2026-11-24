package com.chubb.claims.repo;

import com.chubb.claims.domain.ClaimActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<ClaimActivity, Long> {
    List<ClaimActivity> findByClaimIdOrderByOccurredAtAsc(Long claimId);
}
