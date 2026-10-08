package com.tradingplatform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.api.events.MessageEnvelope;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for publishing ORDER_PLACED events.
 *
 * Producer is configured with:
 * - acks=all: Ensures all replicas acknowledge the message before returning
 * - enable.idempotence=true: Enables idempotent producer to prevent duplicates from retries
 * - retries high: Automatic retries on transient failures
 * - max.in.flight.requests.per.connection=5: At most 5 unacked requests in flight
 *
 * This configuration provides durability and at-least-once delivery semantics.
 */
@Configuration
@EnableKafka
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.max.block.ms:3000}")
    private long maxBlockMs;

    @Value("${spring.kafka.producer.properties.request.timeout.ms:10000}")
    private int requestTimeoutMs;

    @Value("${spring.kafka.producer.properties.delivery.timeout.ms:30000}")
    private int deliveryTimeoutMs;

    /**
     * Configures the ObjectMapper for JSON serialization of MessageEnvelope and payloads.
     * Registers JavaTimeModule to handle Instant (RFC 3339) serialization.
     */
    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Configures ProducerFactory with idempotent and durable producer settings.
     *
     * @return ProducerFactory configured for MessageEnvelope<T>
     */
    @Bean
    public ProducerFactory<String, MessageEnvelope<?>> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Idempotent producer: removes duplicates from producer retries
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Acks=all: message acknowledged by all in-sync replicas before returning
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        
        // High retry count: automatic retries on transient failures
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        // At most 5 unacknowledged requests in flight to maintain ordering
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Fail fast when the broker is down: send() runs on the placement request thread (after commit)
        // and would otherwise block up to 60 s for metadata. A failed ORDER_PLACED is republished by
        // OrderReplayScheduler, whose grace period must stay above delivery.timeout.ms.
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates the KafkaTemplate for sending messages.
     *
     * @return KafkaTemplate for MessageEnvelope with String keys
     */
    @Bean
    public KafkaTemplate<String, MessageEnvelope<?>> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
