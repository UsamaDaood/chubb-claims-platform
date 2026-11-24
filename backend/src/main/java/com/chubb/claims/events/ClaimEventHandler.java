package com.chubb.claims.events;

import com.chubb.claims.domain.Notification;
import com.chubb.claims.repo.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Event consumer: turns domain events into per-user notifications (the claimant's "tracking"
 * feed and the officer's "claimant replied" alert). Idempotent per (user, event).
 */
@Component
public class ClaimEventHandler {

    private final NotificationRepository notifications;
    private final Clock clock;

    public ClaimEventHandler(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void handle(ClaimDomainEvent e) {
        String n = e.claimNumber();
        switch (e.type()) {
            case CLAIM_SUBMITTED -> push(e, e.claimantId(), "We received your claim " + n + ".");
            case CLAIM_ASSIGNED -> push(e, e.claimantId(), "A claims officer has started reviewing " + n + ".");
            case INFO_REQUESTED -> push(e, e.claimantId(), "We need more information to continue with " + n + ".");
            case INFO_PROVIDED -> push(e, e.assignedOfficerId(), "The claimant responded on " + n + ".");
            case CLAIM_APPROVED -> push(e, e.claimantId(), "Good news: " + n + " has been approved.");
            case CLAIM_REJECTED -> push(e, e.claimantId(), "Your claim " + n + " was not accepted. See the decision for details.");
            case CLAIM_SETTLED -> push(e, e.claimantId(), "Payment for " + n + " has been settled.");
            default -> { /* CLAIM_ASSESSED is internal; nobody is notified */ }
        }
    }

    private void push(ClaimDomainEvent e, String userId, String message) {
        if (userId == null || notifications.existsByEventIdAndUserId(e.eventId(), userId)) {
            return;
        }
        notifications.save(new Notification(userId, e.eventId(), e.claimId(), message, clock.instant()));
    }
}
