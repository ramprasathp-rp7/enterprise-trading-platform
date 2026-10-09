package com.tradingplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.type.JdbcType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.config.KafkaProducerConfig;
import com.tradingplatform.api.mybatis.UUIDTypeHandler;

/**
 * Kafka, Jackson, quote-source and MyBatis wiring: the settings the platform relies on.
 */
class InfrastructureConfigTest {

    @Test
    void consumersCommitManuallyFromTheEarliestOffset() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("kafka:9092", "kafka2:9092"));
        KafkaConsumerConfig config = new KafkaConsumerConfig();

        ConsumerFactory<String, String> factory = config.consumerFactory(properties);
        Map<String, Object> props = factory.getConfigurationProperties();

        assertThat(props).containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092,kafka2:9092")
            .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ConcurrentKafkaListenerContainerFactory<String, String> listeners = config.kafkaListenerContainerFactory(factory);
        assertThat(listeners.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(listeners.getConsumerFactory()).isSameAs(factory);
    }

    @Test
    void consumersRefuseToStartWithoutABroker() {
        KafkaProperties properties = mock(KafkaProperties.class);
        when(properties.getBootstrapServers()).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new KafkaConsumerConfig().consumerFactory(properties))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void producersAreIdempotentAndFailFast() throws Exception {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(config, "maxBlockMs", 3000L);
        ReflectionTestUtils.setField(config, "requestTimeoutMs", 10000);
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 30000);

        Map<String, Object> props = ((DefaultKafkaProducerFactory<?, ?>) config.producerFactory()).getConfigurationProperties();

        assertThat(props).containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092")
            .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
            .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000L);
        assertThat(config.kafkaTemplate()).isNotNull();
        assertThat(config.kafkaObjectMapper().writeValueAsString(Instant.parse("2026-10-09T10:00:00Z")))
            .isEqualTo("\"2026-10-09T10:00:00Z\"");
    }

    @Test
    void jacksonWritesIsoDatesAndOptionals() throws Exception {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        assertThat(mapper.writeValueAsString(Instant.parse("2026-10-09T10:00:00Z"))).isEqualTo("\"2026-10-09T10:00:00Z\"");
        assertThat(mapper.writeValueAsString(Optional.of("x"))).isEqualTo("\"x\"");
        assertThat(config.restTemplate()).isNotNull();
    }

    @Test
    void quoteSourceIsReported() {
        assertThat(new QuoteSourceConfig(QuoteSource.KAFKA).getQuoteSource()).isEqualTo(QuoteSource.KAFKA);
        assertThat(new QuoteSourceConfig(QuoteSource.FAUXNANCE).getQuoteSource()).isEqualTo(QuoteSource.FAUXNANCE);
        assertThat(QuoteSource.valueOf("KAFKA")).isEqualTo(QuoteSource.KAFKA);
    }

    @Test
    void uuidsPassThroughJdbcUnchanged() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        UUID id = UUID.randomUUID();
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        CallableStatement call = mock(CallableStatement.class);
        when(rows.getObject("id")).thenReturn(id);
        when(rows.getObject(1)).thenReturn(null);
        when(call.getObject(2)).thenReturn(id);

        handler.setNonNullParameter(statement, 1, id, JdbcType.OTHER);

        verify(statement).setObject(1, id);
        assertThat(handler.getNullableResult(rows, "id")).isEqualTo(id);
        assertThat(handler.getNullableResult(rows, 1)).isNull();
        assertThat(handler.getNullableResult(call, 2)).isEqualTo(id);
    }
}
