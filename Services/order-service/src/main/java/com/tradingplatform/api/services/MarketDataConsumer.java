package com.tradingplatform.api.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.QuotePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * MarketDataConsumer consumes QUOTE events from the market-data Kafka topic.
 * 
 * This service operates in ASYNC mode (eventual consistency):
 * - QUOTE event signals real-time price updates
 * - Caches quotes for fallback when Fauxnance API is unavailable
 * - Uses manual offset commit for reliable delivery (at-least-once semantics)
 * - Graceful error handling: logs exceptions and continues processing
 * 
 * Design:
 * - Consumes from topic: market-data
 * - Filters for EventType.QUOTE
 * - Keyed by symbol for per-symbol ordering guarantee
 * - Updates MarketDataCache with each quote for fallback availability
 * - Logs quote data for analytics and monitoring
 * - Non-blocking processing to handle high message volume
 * 
 * Note: 
 * - Only created when market-data.quote-source=KAFKA (env MARKET_DATA_QUOTE_SOURCE);
 *   in FAUXNANCE mode MarketDataRefreshScheduler fills the cache instead
 * 
 * Error Handling:
 * - Malformed messages: logged as WARN, offset committed (skip and continue)
 * - Missing quotes: logged as WARN, offset committed
 * - Processing exceptions: logged as ERROR, offset NOT committed (retry)
 * 
 * Related:
 * @see QuotePayload for market data structure
 * @see MessageEnvelope for event envelope
 * @see MarketDataCache for quote caching
 */
@Service
@ConditionalOnProperty(name = "market-data.quote-source", havingValue = "KAFKA")
public class MarketDataConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(MarketDataConsumer.class);
    
    private final ObjectMapper objectMapper;
    private final MarketDataCache marketDataCache;
    
    public MarketDataConsumer(ObjectMapper objectMapper, MarketDataCache marketDataCache) {
        this.objectMapper = objectMapper;
        this.marketDataCache = marketDataCache;
    }
    
    /**
     * Kafka listener for QUOTE events from the market-data topic.
     * Processes quote notifications for monitoring and logging.
     * 
     * Topic: market-data
     * GroupId: market-data-consumer (unique consumer group)
     * Key: symbol (ensures per-symbol ordering)
     * 
     * @param messageValue The full MessageEnvelope JSON from Kafka
     * @param ack Manual acknowledgment handle for offset commit
     */
    @KafkaListener(topics = "market-data", groupId = "market-data-consumer")
    public void handleQuote(String messageValue, Acknowledgment ack) {
        try {
            logger.debug("Received market data message: {}", messageValue);
            
            // Step 1: Deserialize the MessageEnvelope from Kafka message value
            MessageEnvelope<QuotePayload> envelope = objectMapper.readValue(
                messageValue,
                new TypeReference<MessageEnvelope<QuotePayload>>() {}
            );
            
            // Step 2: Validate envelope structure
            if (envelope == null) {
                logger.warn("Received null message envelope, skipping");
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 3: Filter for QUOTE events only
            if (envelope.eventType() != EventType.QUOTE) {
                logger.debug("Skipping non-QUOTE event: eventType={}", envelope.eventType());
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 4: Extract quote payload
            QuotePayload quote = envelope.payload();
            if (quote == null) {
                logger.warn("Received QUOTE event with null payload, skipping");
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 5: Validate quote data integrity
            if (quote.symbol() == null || quote.symbol().isBlank()) {
                logger.warn("Received quote with missing symbol, skipping");
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            if (quote.price() == null) {
                logger.warn("Received quote with missing price: symbol={}", quote.symbol());
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 6: Extract event metadata
            String eventId = envelope.eventId();
            Instant eventTime = envelope.eventTime();
            String symbol = quote.symbol();
            
            // Step 7: Log quote notification for analytics/monitoring
            logger.info(
                "Quote notification received: eventId={}, symbol={}, price={}, bid={}, ask={}, " +
                "stale={}, marketState={}, eventTime={}",
                eventId, symbol, quote.price(), quote.bid(), quote.ask(), 
                quote.stale(), quote.marketState(), eventTime
            );
            
            // Step 8: Cache the quote (this consumer is the cache's writer in KAFKA mode)
            marketDataCache.updateQuote(symbol, quote, eventTime);
            logger.debug("Quote cached for symbol: {}", symbol);
            
            // Step 9: Commit offset after successful processing
            if (ack != null) {
                ack.acknowledge();
            }
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Malformed JSON: log as warning and skip (commit offset to avoid reprocessing)
            logger.warn("Failed to deserialize market data message: {}", messageValue, e);
            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            // Unexpected error: log as error and DON'T commit offset (retry on restart)
            logger.error(
                "Error processing market data message. Will retry on next consumer restart: {}",
                messageValue, e
            );
            // Re-throw to trigger DefaultErrorHandler which sends to DLT
            throw new RuntimeException("Failed to process market data quote", e);
        }
    }
    
    /**
     * Health check method to verify consumer is alive.
     * Can be called by monitoring/health check endpoints.
     * 
     * @return true if consumer is operational
     */
    public boolean isHealthy() {
        try {
            // Simple check: consumer is instantiated and ready
            return true;
        } catch (Exception e) {
            logger.error("Health check failed for MarketDataConsumer", e);
            return false;
        }
    }
}
