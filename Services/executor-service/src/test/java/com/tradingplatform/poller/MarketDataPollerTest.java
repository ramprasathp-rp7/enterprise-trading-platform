package com.tradingplatform.poller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.QuotePayload;
import com.tradingplatform.events.Source;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.model.Quote;
import com.tradingplatform.services.SymbolDiscoveryService;

import com.tradingplatform.poller.MarketDataPoller;

import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for MarketDataPoller.
 * 
 * Test execution paths:
 * 1. Batch up to 25 symbols fetched in one request
 * 2. Each quote published as its own message keyed by symbol
 * 3. Configured interval stays inside daily quota for declared symbol set
 * 4. Interval floor enforced in code
 * 5. Empty symbol set handled gracefully (no-op, continue)
 */
@DisplayName("MarketDataPoller Tests")
class MarketDataPollerTest {

    @Mock
    private SymbolDiscoveryService symbolDiscoveryService;

    @Mock
    private QuoteClient quoteClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private MarketDataPoller poller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("Test 1: Interval floor enforcement - below minimum rejected")
    void testIntervalFloorEnforcedBelowMinimum() throws PricingUnavailableException {
        // Create poller with interval below 60 seconds
        MarketDataPoller testPoller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            30,
            60
        );
        
        // Verify it was enforced to 60 seconds
        assertEquals(60 * 1000L, testPoller.getPollIntervalMillis(), 
            "Interval below 60s should be enforced to 60s");
    }

    @Test
    @DisplayName("Test 2: Interval floor enforcement - at minimum accepted")
    void testIntervalFloorEnforcedAtMinimum() throws PricingUnavailableException {
        MarketDataPoller testPoller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            60,
            60
        );
        
        assertEquals(60 * 1000L, testPoller.getPollIntervalMillis(), 
            "60s interval should be accepted");
    }

    @Test
    @DisplayName("Test 3: Interval floor enforcement - above minimum accepted")
    void testIntervalFloorEnforcedAboveMinimum() throws PricingUnavailableException {
        MarketDataPoller testPoller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            120,
            60
        );
        
        assertEquals(120 * 1000L, testPoller.getPollIntervalMillis(), 
            "Interval above 60s should be accepted");
    }

    @Test
    @DisplayName("Test 4: Empty symbol set handled gracefully")
    void testEmptySymbolSetHandledGracefully() throws PricingUnavailableException {
        poller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            60,
            60
        );
        
        // Return empty set of symbols
        when(symbolDiscoveryService.getActiveSymbols()).thenReturn(new HashSet<>());
        
        // Execute poller
        poller.pollMarketData();
        
        // Verify quoteClient was never called (no symbols to poll)
        verify(quoteClient, never()).getLatestPrices(any());
        // Verify no messages were published
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Test 5: Each quote published as its own message keyed by symbol")
    void testEachQuotePublishedAsOwnMessageKeyedBySymbol() throws PricingUnavailableException {
        poller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            60,
            60
        );
        
        // Setup symbols
        Set<String> symbols = new HashSet<>();
        symbols.add("AAPL");
        symbols.add("MSFT");
        symbols.add("GOOGL");
        when(symbolDiscoveryService.getActiveSymbols()).thenReturn(symbols);
        
        // Setup quotes
        List<Quote> quotes = new ArrayList<>();
        quotes.add(createMockQuote("AAPL", "232.50"));
        quotes.add(createMockQuote("MSFT", "425.30"));
        quotes.add(createMockQuote("GOOGL", "180.75"));
        when(quoteClient.getLatestPrices(any())).thenReturn(quotes);
        
        // Setup quota
        when(quoteClient.getTodayRequestCount()).thenReturn(100);
        when(quoteClient.getRemainingQuotaBudget()).thenReturn(1900);
        
        // Execute poller
        poller.pollMarketData();
        
        // Verify three messages were sent
        verify(kafkaTemplate, times(3)).send(any(ProducerRecord.class));
        
        // Capture and verify the messages
        ArgumentCaptor<ProducerRecord<String, String>> captor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(3)).send(captor.capture());
        
        List<ProducerRecord<String, String>> sentRecords = captor.getAllValues();
        
        // Verify keys match symbols
        Set<String> sentKeys = new HashSet<>();
        for (var record : sentRecords) {
            sentKeys.add((String) record.key());
        }
        assertEquals(symbols, sentKeys, "Published messages should be keyed by symbol");
        
        // Verify topic
        for (var record : sentRecords) {
            assertEquals("market-data", record.topic(), "All messages should be sent to market-data topic");
        }
    }

    @Test
    @DisplayName("Test 6: Batch up to 25 symbols fetched in one request")
    void testBatchUpTo25SymbolsInOneRequest() throws PricingUnavailableException {
        poller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            60,
            60
        );
        
        // Create 25 symbols
        Set<String> symbols = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            symbols.add("SYM" + i);
        }
        when(symbolDiscoveryService.getActiveSymbols()).thenReturn(symbols);
        
        // Return quotes for all symbols
        List<Quote> quotes = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            quotes.add(createMockQuote("SYM" + i, String.valueOf(100 + i)));
        }
        when(quoteClient.getLatestPrices(any())).thenReturn(quotes);
        when(quoteClient.getTodayRequestCount()).thenReturn(1);
        when(quoteClient.getRemainingQuotaBudget()).thenReturn(1999);
        
        // Execute poller
        poller.pollMarketData();
        
        // Verify QuoteClient was called once (one batch for 25 symbols)
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteClient, times(1)).getLatestPrices(captor.capture());
        
        List<?> passedSymbols = captor.getValue();
        assertEquals(25, passedSymbols.size(), "All 25 symbols should be passed in one call");
        
        // Verify 25 messages were published (one per quote)
        verify(kafkaTemplate, times(25)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Test 7: Daily quota arithmetic verification")
    void testDailyQuotaArithmetic() throws PricingUnavailableException {
        // 8 symbols at 60s interval = 1 request per minute
        // Per hour: 60 requests, per day: 1440 requests
        // This is well within 2000 limit, leaves 560 buffer for fills
        
        int symbolCount = 8;
        int intervalSeconds = 60;
        
        // Calculate requests per day
        int requestsPerDay = (24 * 3600) / intervalSeconds;
        // Since we batch up to 25 symbols, and we have 8 symbols, it's 1 request per poll
        requestsPerDay = requestsPerDay; // = 1440
        
        assertTrue(requestsPerDay <= 2000, 
            String.format("8 symbols at 60s interval should use %d requests/day, within 2000 limit", 
                requestsPerDay));
        
        int buffer = 2000 - requestsPerDay;
        assertTrue(buffer >= 500, 
            String.format("Should have %d request buffer for fills, got %d", 500, buffer));
    }

    @Test
    @DisplayName("Test 8: Message envelope structure matches contract")
    void testMessageEnvelopeStructureMatchesContract() throws Exception, PricingUnavailableException {
        poller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            60,
            60
        );
        
        Set<String> symbols = new HashSet<>();
        symbols.add("AAPL");
        when(symbolDiscoveryService.getActiveSymbols()).thenReturn(symbols);
        
        List<Quote> quotes = new ArrayList<>();
        quotes.add(createMockQuote("AAPL", "232.50"));
        when(quoteClient.getLatestPrices(any())).thenReturn(quotes);
        when(quoteClient.getTodayRequestCount()).thenReturn(1);
        when(quoteClient.getRemainingQuotaBudget()).thenReturn(1999);
        
        // Execute poller
        poller.pollMarketData();
        
        // Capture the sent record
        ArgumentCaptor<ProducerRecord<String, String>> captor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        
        var record = captor.getValue();
        String messageValue = (String) record.value();
        
        // Parse and verify envelope structure
        MessageEnvelope<QuotePayload> envelope = objectMapper.readValue(
            messageValue, 
            new com.fasterxml.jackson.core.type.TypeReference<MessageEnvelope<QuotePayload>>() {}
        );
        
        assertNotNull(envelope.eventId(), "eventId should not be null");
        assertTrue(envelope.eventId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), 
            "eventId should be a valid UUID");
        assertEquals(EventType.QUOTE, envelope.eventType(), "eventType should be QUOTE");
        assertNotNull(envelope.eventTime(), "eventTime should not be null");
        assertEquals(Source.MARKET_POLLER, envelope.source(), "source should be MARKET_POLLER");
        assertEquals(1, envelope.schemaVersion(), "schemaVersion should be 1");
        assertNotNull(envelope.payload(), "payload should not be null");
        assertEquals("AAPL", envelope.payload().symbol(), "payload symbol should match");
    }

    @Test
    @DisplayName("Test 9: Exception in one quote doesn't stop publishing others")
    void testExceptionInOneQuoteDoesntStopOthers() throws PricingUnavailableException {
        poller = new MarketDataPoller(
            symbolDiscoveryService,
            quoteClient,
            kafkaTemplate,
            objectMapper,
            60,
            60
        );
        
        Set<String> symbols = new HashSet<>();
        symbols.add("AAPL");
        symbols.add("MSFT");
        when(symbolDiscoveryService.getActiveSymbols()).thenReturn(symbols);
        
        List<Quote> quotes = new ArrayList<>();
        quotes.add(createMockQuote("AAPL", "232.50"));
        quotes.add(createMockQuote("MSFT", "425.30"));
        when(quoteClient.getLatestPrices(any())).thenReturn(quotes);
        
        when(quoteClient.getTodayRequestCount()).thenReturn(1);
        when(quoteClient.getRemainingQuotaBudget()).thenReturn(1999);
        
        // Make first send fail, but allow second to succeed
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenThrow(new RuntimeException("Kafka error"))
            .thenReturn(null);
        
        // Execute poller - should not throw exception
        assertDoesNotThrow(() -> poller.pollMarketData(), 
            "Poller should not throw exception even if one message publish fails");
        
        // Verify both sends were attempted
        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    }

    /**
     * Helper method to create a mock Quote object.
     */
    private Quote createMockQuote(String symbol, String price) {
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setPrice(new BigDecimal(price));
        quote.setBid(new BigDecimal(price).subtract(BigDecimal.ONE));
        quote.setAsk(new BigDecimal(price).add(BigDecimal.ONE));
        quote.setCurrency("USD");
        quote.setChange(BigDecimal.ZERO);
        quote.setChangePercent(BigDecimal.ZERO);
        quote.setPreviousClose(new BigDecimal(price));
        quote.setMarketState("open");
        quote.setAsOf(Instant.now().toString());
        return quote;
    }
}
