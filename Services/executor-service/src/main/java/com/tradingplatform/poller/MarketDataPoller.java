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
 * Polls every instrument symbol at a configurable interval, batches up to 25 symbols per request,
 * and publishes one message per symbol to maintain per-symbol ordering guarantees.
 * 
 * Quota tracking: Shares the same Fauxnance request budget with the fill path (both use QuoteClient).
 * The local quota counter in QuoteClient tracks daily requests and auto-resets at UTC midnight.
 * 
 * Configuration (via environment variables or application.properties):
 * - poll.interval.seconds: Poll interval in seconds (default: 300, every five minutes). Will be clamped to min.poll.interval.seconds if lower.
 * - min.poll.interval.seconds: Minimum allowed poll interval in seconds (default: 60). Cannot be configured below this value.
 * 
 * Examples:
 *   export POLL_INTERVAL_SECONDS=30  # Will be enforced to min.poll.interval.seconds if that's higher
 *   export MIN_POLL_INTERVAL_SECONDS=45  # Override minimum interval requirement
 */
@Component
@EnableScheduling
public class MarketDataPoller {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataPoller.class);
    
    private static final String MARKET_DATA_TOPIC = "market-data";

    private final SymbolDiscoveryService symbolDiscoveryService;
    private final QuoteClient quoteClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int pollIntervalSeconds;
    private final int minPollIntervalSeconds;

    public MarketDataPoller(
            SymbolDiscoveryService symbolDiscoveryService,
            QuoteClient quoteClient,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${poll.interval.seconds:300}")
            int pollIntervalSeconds,
            @Value("${min.poll.interval.seconds:60}")
            int minPollIntervalSeconds) {
        this.symbolDiscoveryService = symbolDiscoveryService;
        this.quoteClient = quoteClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.minPollIntervalSeconds = minPollIntervalSeconds;
        
        // Validate and enforce minimum interval
        if (pollIntervalSeconds < minPollIntervalSeconds) {
            logger.warn("Configured poll interval {}s is below minimum {}s; enforcing minimum",
                    pollIntervalSeconds, minPollIntervalSeconds);
            this.pollIntervalSeconds = minPollIntervalSeconds;
        } else {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }
        
        logger.info("MarketDataPoller initialized with interval: {}s (min: {}s)", this.pollIntervalSeconds, this.minPollIntervalSeconds);
    }

    /**
     * Scheduled method that polls for market data at the configured interval.
        * The interval is read from poll.interval.seconds (default 300s, every five minutes) and validated against
        * min.poll.interval.seconds. Note the schedule itself reads poll.interval.seconds directly, so the
     * clamp in the constructor only logs a warning; keep the property at or above the minimum.
     * Both values can be configured via environment variables or application.properties.
     * 
     * Note: This polls continuously at a fixed rate. The interval is validated at initialization time.
     * If configured interval is below the minimum, it will be clamped to the minimum value.
     */
    @Scheduled(fixedDelayString = "${poll.interval.seconds:300}000")
    public void pollMarketData() {
        try {
            logger.info("Starting market data poll cycle");
            
            // Get symbols to poll: every instrument
            Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
            
            if (symbols.isEmpty()) {
                logger.info("No instruments to poll, skipping this cycle");
                return;
            }
            
            logger.info("Polling {} symbols for market data", symbols.size());
            
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
            
            logger.info("Completed market data poll cycle, published {} quotes", quotes.size());
            
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
        logger.info("Published quote to market-data: symbol={}", symbol);
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

    // Expose poll interval as seconds for external configuration and tests
    public int getPollIntervalSeconds() {
        return this.pollIntervalSeconds;
    }
}
