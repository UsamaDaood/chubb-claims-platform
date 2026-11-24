package com.chubb.claims.repo;

import com.chubb.claims.domain.InfoRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InfoRequestRepository extends JpaRepository<InfoRequest, Long> {
    List<InfoRequest> findByClaimIdOrderByIdAsc(Long claimId);

    long countByClaimIdAndAnsweredAtIsNull(Long claimId);
}
