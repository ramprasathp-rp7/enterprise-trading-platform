package com.tradingplatform.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@EnableKafka
public class KafkaListenerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.max.block.ms:3000}")
    private long maxBlockMs = 3000L;

    @Value("${spring.kafka.producer.properties.request.timeout.ms:10000}")
    private int requestTimeoutMs = 10000;

    @Value("${spring.kafka.producer.properties.delivery.timeout.ms:30000}")
    private int deliveryTimeoutMs = 30000;

    @Bean
    public ConsumerFactory<String, String> orderConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "trade-executor");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual ack
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    @SuppressWarnings({"unchecked", "null"})
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        ConsumerFactory<? super String, ? super String> consumerFactory = (ConsumerFactory<? super String, ? super String>) (ConsumerFactory<?, ?>) orderConsumerFactory();
        factory.setConsumerFactory(consumerFactory);
        // Set concurrency to 3 (matches 3 partitions on orders topic)
        factory.setConcurrency(3);
        // Enable manual acknowledgment: messages only acknowledged after successful processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Producer for trade-events topic (where executor publishes filled/rejected events).
     * Configured for idempotent production with full acks.
     */
    @Bean
    public ProducerFactory<String, String> tradeEventsProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        putFailFastTimeouts(configProps);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> tradeEventsKafkaTemplate() {
        return new KafkaTemplate<>(tradeEventsProducerFactory());
    }

    /**
     * Producer for dead-letter topic (where bad messages and retry-exhausted orders go).
     * Same configuration as trade-events: idempotent with full acks.
     */
    @Bean
    public ProducerFactory<String, String> deadLetterProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        putFailFastTimeouts(configProps);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Fail fast when the broker is down instead of blocking up to 60 s for metadata. A failed trade
     * event stays pending for OutcomeReplayScheduler, whose grace period must stay above
     * delivery.timeout.ms; a failed dead-letter send is nacked and redelivered.
     */
    private void putFailFastTimeouts(Map<String, Object> configProps) {
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
    }

    @Bean
    @SuppressWarnings({"unchecked", "null"})
    public KafkaTemplate<String, String> deadLetterKafkaTemplate() {
        ProducerFactory<String, String> producerFactory = (ProducerFactory<String, String>) (ProducerFactory<?, ?>) deadLetterProducerFactory();
        return new KafkaTemplate<>(producerFactory);
    }
    
    @Bean
    @Primary
    @SuppressWarnings({"unchecked", "null"})
    public KafkaTemplate<String, String> kafkaTemplate() {
        ProducerFactory<String, String> producerFactory = (ProducerFactory<String, String>) (ProducerFactory<?, ?>) deadLetterProducerFactory();
        return new KafkaTemplate<>(producerFactory);
    }
}
