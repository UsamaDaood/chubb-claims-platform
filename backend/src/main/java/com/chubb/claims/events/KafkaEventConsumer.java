package com.chubb.claims.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;

/** Kafka-side wiring: topic declaration + the consumer that drives the same handler as in-process mode. */
@Configuration
@ConditionalOnProperty(name = "claims.events.transport", havingValue = "kafka")
public class KafkaEventConsumer {

    private final ClaimEventHandler handler;
    private final ObjectMapper mapper;

    public KafkaEventConsumer(ClaimEventHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @Bean
    NewTopic claimsEventsTopic(@Value("${claims.events.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @KafkaListener(topics = "${claims.events.topic}", groupId = "claims-notifications")
    public void onMessage(String payload) throws JsonProcessingException {
        handler.handle(mapper.readValue(payload, ClaimDomainEvent.class));
    }
}
