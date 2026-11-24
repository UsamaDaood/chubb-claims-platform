package com.chubb.claims.service;

import com.chubb.claims.api.Dtos.NotificationView;
import com.chubb.claims.common.ApiException;
import com.chubb.claims.domain.Notification;
import com.chubb.claims.repo.NotificationRepository;
import com.chubb.claims.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(AuthUser actor) {
        return repo.findTop50ByUserIdOrderByCreatedAtDesc(actor.id()).stream()
                .map(n -> new NotificationView(n.getId(), n.getClaimId(), n.getMessage(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
    }

    @Transactional
    public void markRead(AuthUser actor, Long id) {
        Notification n = repo.findById(id)
                .filter(x -> x.getUserId().equals(actor.id()))
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        n.markRead(clock.instant());
    }
}
