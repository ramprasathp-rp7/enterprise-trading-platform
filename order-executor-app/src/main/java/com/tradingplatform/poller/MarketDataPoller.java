package com.tradingplatform.poller;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.events.EventType;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.QuotePayload;
import com.tradingplatform.events.Source;
import com.tradingplatform.quote.PricingUnavailableException;
import com.tradingplatform.quote.QuoteClient;
import com.tradingplatform.quote.model.Quote;
import com.tradingplatform.services.SymbolDiscoveryService;

/**
 * Scheduled market data poller that publishes quotes from Fauxnance to the Kafka market-data topic.
 * 
 * Polls active symbols (from positions) at a configurable interval, batches up to 25 symbols per request,
 * and publishes one message per symbol to maintain per-symbol ordering guarantees.
 * 
 * Quota tracking: Shares the same Fauxnance request budget with the fill path (both use QuoteClient).
 * The local quota counter in QuoteClient tracks daily requests and auto-resets at UTC midnight.
 * 
 * Poll schedule can be controlled via POLL_INTERVAL_SECONDS environment variable (default 60s).
 * Minimum interval is enforced at 60 seconds in code.
 */
@Component
@EnableScheduling
public class MarketDataPoller {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataPoller.class);
    
    private static final String MARKET_DATA_TOPIC = "market-data";
    private static final int MIN_POLL_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 60;

    private final SymbolDiscoveryService symbolDiscoveryService;
    private final QuoteClient quoteClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int pollIntervalSeconds;

    public MarketDataPoller(
            SymbolDiscoveryService symbolDiscoveryService,
            QuoteClient quoteClient,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${POLL_INTERVAL_SECONDS:#{T(java.lang.System).getenv('POLL_INTERVAL_SECONDS') != null ? T(java.lang.Integer).parseInt(T(java.lang.System).getenv('POLL_INTERVAL_SECONDS')) : 60}}")
            int pollIntervalSeconds) {
        this.symbolDiscoveryService = symbolDiscoveryService;
        this.quoteClient = quoteClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        // Validate and enforce minimum interval
        if (pollIntervalSeconds < MIN_POLL_INTERVAL_SECONDS) {
            logger.warn("Configured poll interval {}s is below minimum {}s; enforcing minimum",
                    pollIntervalSeconds, MIN_POLL_INTERVAL_SECONDS);
            this.pollIntervalSeconds = MIN_POLL_INTERVAL_SECONDS;
        } else {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }
        
        logger.info("MarketDataPoller initialized with interval: {}s", this.pollIntervalSeconds);
    }

    /**
     * Scheduled method that polls for market data at the configured interval.
     * The interval is controlled by the fixedRate parameter which is set to match
     * the configured poll interval in milliseconds.
     * 
     * Note: This polls continuously at a fixed rate. The interval itself is validated
     * at initialization time and cannot be changed at runtime without redeployment.
     */
    @Scheduled(fixedDelayString = "#{@marketDataPoller.getPollIntervalMillis()}")
    public void pollMarketData() {
        try {
            logger.debug("Starting market data poll cycle");
            
            // Get symbols to poll from active positions
            Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
            
            if (symbols.isEmpty()) {
                logger.debug("No active symbols to poll, skipping this cycle");
                return;
            }
            
            logger.debug("Polling {} symbols for market data", symbols.size());
            
            // Fetch quotes for all symbols in batches of 25
            List<Quote> quotes;
            try {
                quotes = quoteClient.getLatestPrices(new java.util.ArrayList<>(symbols));
            } catch (PricingUnavailableException e) {
                logger.warn("Failed to fetch quotes for {} symbols: {}", symbols.size(), e.getMessage());
                return;
            }
            
            // Publish one message per quote to market-data topic
            for (Quote quote : quotes) {
                try {
                    publishQuoteToKafka(quote);
                } catch (Exception e) {
                    logger.warn("Failed to publish quote for symbol {}: {}", 
                            quote.getSymbol(), e.getMessage());
                    // Continue with next quote, don't stop on individual failures
                }
            }
            
            // Log quota status
            logQuotaStatus();
            
            logger.debug("Completed market data poll cycle, published {} quotes", quotes.size());
            
        } catch (Exception e) {
            logger.error("Unexpected error in market data poller: {}", e.getMessage(), e);
            // Don't rethrow; let the scheduler continue for next cycle
        }
    }

    /**
     * Publish a single quote as a MessageEnvelope to the market-data topic, keyed by symbol.
     */
    private void publishQuoteToKafka(Quote quote) throws Exception {
        String symbol = quote.getSymbol();
        
        QuotePayload payload = new QuotePayload(
            symbol,
            quote.getPrice(),
            quote.getBid(),
            quote.getAsk(),
            quote.getCurrency(),
            quote.getChange(),
            quote.getChangePercent(),
            quote.getPreviousClose(),
            quote.getMarketState(),
            false, // stale - we just fetched it
            Instant.parse(quote.getAsOf()) // quoteAsOf
        );
        
        MessageEnvelope<QuotePayload> envelope = new MessageEnvelope<>(
            UUID.randomUUID().toString(),
            EventType.QUOTE,
            Instant.now(),
            Source.MARKET_POLLER,
            payload
        );
        
        String messageValue = objectMapper.writeValueAsString(envelope);
        ProducerRecord<String, String> record = new ProducerRecord<>(
            MARKET_DATA_TOPIC,
            symbol, // Key: symbol for per-symbol ordering
            messageValue
        );
        
        kafkaTemplate.send(record);
        logger.debug("Published quote to market-data: symbol={}", symbol);
    }

    /**
     * Log current quota usage and warn if approaching limit.
     */
    private void logQuotaStatus() {
        int used = quoteClient.getTodayRequestCount();
        int remaining = quoteClient.getRemainingQuotaBudget();
        
        logger.info("Fauxnance quota status: {} used, {} remaining out of 2000/day", used, remaining);
        
        if (remaining < 100) {
            logger.warn("Approaching Fauxnance quota limit: only {} requests remaining for today!", remaining);
        }
    }

    /**
     * Get poll interval in milliseconds.
     * Needed for Spring @Scheduled fixedDelayString evaluation.
     */
    public long getPollIntervalMillis() {
        return pollIntervalSeconds * 1000L;
    }
}
