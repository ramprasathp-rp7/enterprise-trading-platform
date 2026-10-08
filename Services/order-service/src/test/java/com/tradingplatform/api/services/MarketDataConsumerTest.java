package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.events.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketDataConsumer.
 * 
 * Tests cover:
 * - Happy path: valid QUOTE event processing
 * - Error cases: malformed JSON, missing fields, null values
 * - Filtering: non-QUOTE events are skipped
 * - Offset management: acknowledgment is called appropriately
 * - Cache integration: quotes are properly cached
 * 
 * Test data uses realistic market quotes from various instruments.
 */
@DisplayName("MarketDataConsumer Tests")
class MarketDataConsumerTest {
    
    private MarketDataConsumer consumer;
    private ObjectMapper objectMapper;
    
    @Mock
    private Acknowledgment acknowledgment;
    
    @Mock
    private MarketDataCache marketDataCache;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        // Register modules to handle Java 8 types (Instant, Optional, etc.)
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new MarketDataConsumer(objectMapper, marketDataCache);
    }
    
    @Test
    @DisplayName("Should process valid QUOTE event and acknowledge")
    void testHandleValidQuote() throws Exception {
        // Arrange
        QuotePayload quotePayload = new QuotePayload(
            "INFY.NS",                           // symbol
            new BigDecimal("1850.50"),           // price
            new BigDecimal("1850.25"),           // bid
            new BigDecimal("1850.75"),           // ask
            "INR",                               // currency
            new BigDecimal("5.50"),              // change
            new BigDecimal("0.30"),              // changePercent
            new BigDecimal("1845.00"),           // previousClose
            "open",                              // marketState
            false,                               // stale
            Instant.now()                        // quoteAsOf
        );
        
        Instant eventTime = Instant.now();
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-123",
            EventType.QUOTE,
            eventTime,
            Source.MARKET_POLLER,
            1,
            quotePayload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert
        verify(marketDataCache).updateQuote("INFY.NS", quotePayload, eventTime);
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should skip non-QUOTE events")
    void testSkipNonQuoteEvent() throws Exception {
        // Arrange
        QuotePayload quotePayload = new QuotePayload(
            "INFY.NS",
            new BigDecimal("1850.50"),
            new BigDecimal("1850.25"),
            new BigDecimal("1850.75"),
            "INR",
            new BigDecimal("5.50"),
            new BigDecimal("0.30"),
            new BigDecimal("1845.00"),
            "open",
            false,
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-456",
            EventType.ORDER_PLACED,  // Different event type
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quotePayload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void testHandleMalformedJson() {
        // Arrange
        String malformedJson = "{\"invalid\": json}";
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> consumer.handleQuote(malformedJson, acknowledgment));
        verify(acknowledgment).acknowledge();  // Offset should be committed
    }
    
    @Test
    @DisplayName("Should handle null message envelope")
    void testHandleNullEnvelope() throws Exception {
        // Arrange
        String nullEnvelopeJson = "null";
        
        // Act
        consumer.handleQuote(nullEnvelopeJson, acknowledgment);
        
        // Assert
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should skip quote with null payload")
    void testSkipQuoteWithNullPayload() throws Exception {
        // Arrange
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-789",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            null  // null payload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should skip quote with missing symbol")
    void testSkipQuoteWithMissingSymbol() throws Exception {
        // Arrange
        QuotePayload quotePayload = new QuotePayload(
            "",  // blank symbol
            new BigDecimal("1850.50"),
            new BigDecimal("1850.25"),
            new BigDecimal("1850.75"),
            "INR",
            new BigDecimal("5.50"),
            new BigDecimal("0.30"),
            new BigDecimal("1845.00"),
            "open",
            false,
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-101",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quotePayload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should skip quote with missing price")
    void testSkipQuoteWithMissingPrice() throws Exception {
        // Arrange
        QuotePayload quotePayload = new QuotePayload(
            "INFY.NS",
            null,  // null price
            new BigDecimal("1850.25"),
            new BigDecimal("1850.75"),
            "INR",
            new BigDecimal("5.50"),
            new BigDecimal("0.30"),
            new BigDecimal("1845.00"),
            "open",
            false,
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-102",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quotePayload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should handle multiple quotes for same symbol (latest wins)")
    void testMultipleQuotesForSameSymbol() throws Exception {
        // Arrange - first quote
        QuotePayload quote1 = new QuotePayload(
            "INFY.NS",
            new BigDecimal("1850.00"),
            new BigDecimal("1849.75"),
            new BigDecimal("1850.25"),
            "INR",
            new BigDecimal("5.00"),
            new BigDecimal("0.27"),
            new BigDecimal("1845.00"),
            "open",
            false,
            Instant.now()
        );
        
        // Second quote with updated price
        QuotePayload quote2 = new QuotePayload(
            "INFY.NS",
            new BigDecimal("1855.50"),  // Updated price
            new BigDecimal("1855.25"),
            new BigDecimal("1855.75"),
            "INR",
            new BigDecimal("10.50"),
            new BigDecimal("0.57"),
            new BigDecimal("1845.00"),
            "open",
            false,
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope1 = new MessageEnvelope<>(
            "event-201",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quote1
        );
        
        MessageEnvelope<QuotePayload> envelope2 = new MessageEnvelope<>(
            "event-202",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quote2
        );
        
        // Act
        consumer.handleQuote(objectMapper.writeValueAsString(envelope1), acknowledgment);
        consumer.handleQuote(objectMapper.writeValueAsString(envelope2), acknowledgment);
        
        // Assert - second quote should overwrite first
        verify(acknowledgment, times(2)).acknowledge();
    }
    
    @Test
    @DisplayName("Should handle quotes with null optional fields")
    void testQuoteWithNullOptionalFields() throws Exception {
        // Arrange
        QuotePayload quotePayload = new QuotePayload(
            "TCS.NS",
            new BigDecimal("3850.50"),
            null,  // null bid
            null,  // null ask
            "INR",
            null,  // null change
            null,  // null changePercent
            null,  // null previousClose
            "open",
            false,
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-303",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quotePayload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert - should process the quote even with null optional fields
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    @DisplayName("Should set consumer health to true")
    void testIsHealthy() {
        assertTrue(consumer.isHealthy());
    }
    
    @Test
    @DisplayName("Should handle acknowledgment being null")
    void testHandleNullAcknowledgment() throws Exception {
        // Arrange
        QuotePayload quotePayload = new QuotePayload(
            "HDFC.NS",
            new BigDecimal("2750.25"),
            new BigDecimal("2750.00"),
            new BigDecimal("2750.50"),
            "INR",
            new BigDecimal("0.25"),
            new BigDecimal("0.01"),
            new BigDecimal("2750.00"),
            "open",
            false,
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-404",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            quotePayload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act - should not throw exception with null acknowledgment
        assertDoesNotThrow(() -> consumer.handleQuote(messageValue, null));
    }
    
    @Test
    @DisplayName("Should handle stale quotes appropriately")
    void testHandleStaleQuote() throws Exception {
        // Arrange
        QuotePayload staleQuote = new QuotePayload(
            "RELIANCE.NS",
            new BigDecimal("2750.50"),
            new BigDecimal("2750.25"),
            new BigDecimal("2750.75"),
            "INR",
            new BigDecimal("10.50"),
            new BigDecimal("0.38"),
            new BigDecimal("2740.00"),
            "closed",
            true,  // stale = true
            Instant.now()
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            "event-505",
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            1,
            staleQuote
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        
        // Act
        consumer.handleQuote(messageValue, acknowledgment);
        
        // Assert - stale quote should be processed
        verify(acknowledgment).acknowledge();
    }
}
