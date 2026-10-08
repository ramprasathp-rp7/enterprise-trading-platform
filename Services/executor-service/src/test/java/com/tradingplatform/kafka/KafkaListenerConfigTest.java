package com.tradingplatform.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaListenerConfigTest {

    private KafkaListenerConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaListenerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    }

    @Test
    void consumerFactoryUsesManualCommitAndExecutorGroup() {
        Map<String, Object> props = config.orderConsumerFactory().getConfigurationProperties();

        assertEquals("localhost:9092", props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("trade-executor", props.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals(false, props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals("earliest", props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
    }

    @Test
    void listenerFactoryUsesManualImmediateAck() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = config.kafkaListenerContainerFactory();

        assertNotNull(factory.getConsumerFactory());
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE, factory.getContainerProperties().getAckMode());
    }

    @Test
    void producersAreIdempotentWithFullAcks() {
        Map<String, Object> tradeProps = config.tradeEventsProducerFactory().getConfigurationProperties();
        Map<String, Object> dltProps = config.deadLetterProducerFactory().getConfigurationProperties();

        for (Map<String, Object> props : new Map[] {tradeProps, dltProps}) {
            assertEquals("localhost:9092", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
            assertEquals("all", props.get(ProducerConfig.ACKS_CONFIG));
            assertEquals(true, props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        }
        assertEquals(3, tradeProps.get(ProducerConfig.RETRIES_CONFIG));
    }

    @Test
    void templatesAreCreatedWithoutConnecting() {
        assertNotNull(config.tradeEventsKafkaTemplate().getProducerFactory());
        assertNotNull(config.deadLetterKafkaTemplate().getProducerFactory());
        assertNotNull(config.kafkaTemplate().getProducerFactory());
    }

    @Test
    void producersFailFastWhenTheBrokerIsDown() {
        for (Map<String, Object> props : List.of(config.tradeEventsProducerFactory().getConfigurationProperties(),
                                                 config.deadLetterProducerFactory().getConfigurationProperties())) {
            assertEquals(3000L, props.get(ProducerConfig.MAX_BLOCK_MS_CONFIG));
            assertEquals(10000, props.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG));
            assertEquals(30000, props.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG));
        }
    }
}
