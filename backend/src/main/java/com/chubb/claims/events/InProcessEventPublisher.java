package com.chubb.claims.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default transport so the app runs with zero infrastructure. Same handler, same payload as Kafka. */
@Component
@ConditionalOnProperty(name = "claims.events.transport", havingValue = "inprocess", matchIfMissing = true)
public class InProcessEventPublisher implements EventPublisher {

    private final ClaimEventHandler handler;
    private final ObjectMapper mapper;

    public InProcessEventPublisher(ClaimEventHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @Override
    public void publish(OutboxMessage message) throws Exception {
        handler.handle(mapper.readValue(message.getPayload(), ClaimDomainEvent.class));
    }
}
