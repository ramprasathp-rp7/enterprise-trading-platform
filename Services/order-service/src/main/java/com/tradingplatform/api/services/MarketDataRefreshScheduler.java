package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.mappers.PositionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Refreshes MarketDataCache from the Fauxnance quotes API every five minutes.
 *
 * Only created when market-data.quote-source=FAUXNANCE (the default; env MARKET_DATA_QUOTE_SOURCE).
 * In KAFKA mode MarketDataConsumer fills the cache from the market-data topic instead.
 *
 * Each run quotes the same symbols as the executor's market-data poller (tradable symbols
 * with a non-zero position), in batched calls of up to 25 symbols per request, so the daily
 * cost is 288 requests per 25 held symbols, well inside the 2000 request quota.
 * Every quote is appended to the cache's per-symbol history; symbols Fauxnance fails to
 * quote simply get no new entry that run.
 *
 * Related:
 * @see MarketDataCache for the cache
 * @see com.tradingplatform.config.QuoteSource for the source switch
 */
@Component
@ConditionalOnProperty(name = "market-data.quote-source", havingValue = "FAUXNANCE", matchIfMissing = true)
public class MarketDataRefreshScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataRefreshScheduler.class);

    private final FauxnanceApiClient fauxnanceClient;
    private final PositionMapper positionMapper;
    private final MarketDataCache marketDataCache;

    public MarketDataRefreshScheduler(FauxnanceApiClient fauxnanceClient, PositionMapper positionMapper,
                                      MarketDataCache marketDataCache) {
        this.fauxnanceClient = Objects.requireNonNull(fauxnanceClient, "fauxnanceClient cannot be null");
        this.positionMapper = Objects.requireNonNull(positionMapper, "positionMapper cannot be null");
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
    }

    /**
     * Quotes every held symbol and appends the results to the cache.
     * Runs on startup, then every market-data.refresh.interval-ms (default five minutes).
     */
    @Scheduled(initialDelayString = "${market-data.refresh.initial-delay-ms:0}",
               fixedRateString = "${market-data.refresh.interval-ms:300000}")
    public void refreshQuotes() {
        List<String> symbols;
        try {
            symbols = positionMapper.getDistinctSymbolsWithQuantity();
        } catch (Exception ex) {
            logger.error("Quote refresh skipped: could not load held symbols: {}", ex.getMessage());
            return;
        }
        if (symbols == null || symbols.isEmpty()) {
            logger.debug("Quote refresh skipped: no held symbols");
            return;
        }

        Map<String, FauxnanceApiClient.QuoteData> quotes;
        try {
            quotes = fauxnanceClient.getBatchedQuotes(symbols);
        } catch (Exception ex) {
            logger.warn("Quote refresh failed, keeping cached quotes: {}", ex.getMessage());
            return;
        }

        Instant receivedAt = Instant.now();
        quotes.values().forEach(quote ->
            marketDataCache.updateQuote(quote.getSymbol(), toPayload(quote), receivedAt));

        if (quotes.size() < symbols.size()) {
            logger.warn("Quote refresh: Fauxnance quoted {} of {} symbols; the rest get no new quote this run",
                quotes.size(), symbols.size());
        } else {
            logger.info("Quote refresh: cached {} quotes from Fauxnance", quotes.size());
        }
    }

    /** Fauxnance quotes carry no change or market state fields, so those stay empty. */
    private static QuotePayload toPayload(FauxnanceApiClient.QuoteData quote) {
        return new QuotePayload(
            quote.getSymbol(),
            quote.getPrice(),
            quote.getBid(),
            quote.getAsk(),
            quote.getCurrency(),
            null,
            null,
            null,
            null,
            quote.isStale(),
            quote.getQuoteAsOf()
        );
    }
}
