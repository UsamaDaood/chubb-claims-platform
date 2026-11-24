package com.chubb.claims.events;

import com.chubb.claims.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox and hands rows to the configured transport. Delivery is at-least-once:
 * a crash between publish and markPublished re-sends, so consumers must be idempotent
 * (ours are: unique (user_id, event_id)). Stops at the first failure to preserve ordering.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repo;
    private final OutboxService outbox;
    private final EventPublisher publisher;

    public OutboxRelay(OutboxRepository repo, OutboxService outbox, EventPublisher publisher) {
        this.repo = repo;
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.poll-ms:500}")
    public void drain() {
        for (OutboxMessage m : repo.findTop100ByPublishedAtIsNullOrderByIdAsc()) {
            try {
                publisher.publish(m);
                outbox.markPublished(m.getId());
            } catch (Exception e) {
                log.warn("Outbox publish failed for event {}; will retry: {}", m.getEventId(), e.toString());
                return;
            }
        }
    }
}
