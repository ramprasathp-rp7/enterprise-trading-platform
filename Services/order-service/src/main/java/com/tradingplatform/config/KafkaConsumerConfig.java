package com.tradingplatform.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration for Trade REST API.
 *
 * Configures Spring Kafka consumer factories with the following settings:
 * - auto-offset-reset=earliest: New consumer groups read retained topic history
 * - enable-auto-commit=false: Manual offset commit for at-least-once delivery
 * - ack-mode=manual_immediate: Commit offset immediately after successful processing
 * - max-poll-records=100: Batch size for efficient processing
 * - session-timeout-ms=10000: Detect dead consumers and trigger rebalance
 *
 * These settings ensure reliable message delivery and prevent data loss on crashes.
 *
 * Used by:
 * - Trade Executor (consumes orders topic via OrderPlacedListener)
 * - Extension modules in Sprint 10 (portfolio, watchlist, notification, advice, strategy)
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /**
     * Create a consumer factory with proper Kafka consumer configuration.
     * This factory is used by all @KafkaListener annotated methods in the application.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> consumerProps = new HashMap<>();
        
        // Bootstrap servers: from KafkaProperties or application.properties
        if (kafkaProperties.getBootstrapServers() == null || kafkaProperties.getBootstrapServers().isEmpty()) {
            throw new IllegalStateException("spring.kafka.bootstrap-servers is not configured (EC2_PRIVATE_IP and kafka.port)");
        }
        String bootstrapServers = String.join(",", kafkaProperties.getBootstrapServers());
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Key and value deserializers
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        
        // Override with explicit settings for reliable delivery
        // auto-offset-reset=earliest: Read the retained message history for new consumer groups
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // enable-auto-commit=false: Disable automatic offset commits
        // We use manual commit (ack-mode=manual_immediate) for safer delivery
        // This ensures we only commit after successful processing
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Batch size: how many records to poll at once from the broker
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        // Session timeout: time to detect dead consumer before triggering rebalance
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        
        return new DefaultKafkaConsumerFactory<>(consumerProps);
    }

    /**
     * Create a listener container factory for @KafkaListener methods.
     * This factory controls how messages are delivered and acknowledged.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Manual immediate ack mode: commit offset immediately after listener returns successfully
        // This is safer than auto-commit because offset is only committed after processing is complete
        // If the listener throws an exception, the offset is not committed and the message will be retried
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Poll timeout: how long to wait for records from the broker
        factory.getContainerProperties().setPollTimeout(3000);
        
        // Error handler: sends malformed messages to DLT (dead letter topic)
        // This prevents a single bad message from blocking all subsequent messages
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        
        return factory;
    }
}
