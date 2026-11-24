package com.chubb.claims.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "claims.events.transport", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka,
                               @Value("${claims.events.topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    @Override
    public void publish(OutboxMessage message) throws Exception {
        // Block for the broker ack: the outbox row is only marked published once Kafka has it.
        kafka.send(topic, message.getKey(), message.getPayload()).get(10, TimeUnit.SECONDS);
    }
}
