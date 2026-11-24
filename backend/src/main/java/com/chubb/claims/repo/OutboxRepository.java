package com.chubb.claims.repo;

import com.chubb.claims.events.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
