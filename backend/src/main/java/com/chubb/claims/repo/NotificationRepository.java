package com.chubb.claims.repo;

import com.chubb.claims.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByEventIdAndUserId(String eventId, String userId);
}
