package com.chubb.claims.events;

/** Transport abstraction for outbox delivery. Implementations must throw if delivery is not confirmed. */
public interface EventPublisher {
    void publish(OutboxMessage message) throws Exception;
}
