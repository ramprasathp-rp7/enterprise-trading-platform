package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.InstrumentMapper;
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
 * Each run quotes every symbol in the instruments table, the same symbols the executor's
 * market-data poller publishes to the topic, in batched calls of up to 25 symbols per request,
 * so the daily cost is 288 requests per 25 instruments, well inside the 2000 request quota.
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
    private final InstrumentMapper instrumentMapper;
    private final MarketDataCache marketDataCache;

    public MarketDataRefreshScheduler(FauxnanceApiClient fauxnanceClient, InstrumentMapper instrumentMapper,
                                      MarketDataCache marketDataCache) {
        this.fauxnanceClient = Objects.requireNonNull(fauxnanceClient, "fauxnanceClient cannot be null");
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper, "instrumentMapper cannot be null");
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
    }

    /**
     * Quotes every instrument and appends the results to the cache.
     * Runs on startup, then every market-data.refresh.interval-ms (default five minutes).
     */
    @Scheduled(initialDelayString = "${market-data.refresh.initial-delay-ms:0}",
               fixedRateString = "${market-data.refresh.interval-ms:300000}")
    public void refreshQuotes() {
        List<String> symbols;
        try {
            symbols = withConversionFxSymbols(instrumentMapper.findAllSymbols());
        } catch (Exception ex) {
            logger.error("Quote refresh skipped: could not load instrument symbols: {}", ex.getMessage());
            return;
        }
        if (symbols == null || symbols.isEmpty()) {
            logger.debug("Quote refresh skipped: no instruments");
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
            marketDataCache.updateQuote(quote.getSymbol(), quote.toPayload(), receivedAt));

        if (quotes.size() < symbols.size()) {
            List<String> unquoted = symbols.stream().filter(symbol -> !quotes.containsKey(symbol)).toList();
            logger.warn("Quote refresh: Fauxnance quoted {} of {} symbols; no quote this run for {}",
                quotes.size(), symbols.size(), unquoted);
        } else {
            logger.info("Quote refresh: cached {} quotes from Fauxnance", quotes.size());
        }
    }

    /** Adds the FX pairs that convert instrument currencies into account currencies (multi-currency). */
    private List<String> withConversionFxSymbols(List<String> symbols) {
        if (symbols == null) {
            return null;
        }
        List<String> fxSymbols = instrumentMapper.findConversionFxSymbols();
        if (fxSymbols == null || fxSymbols.isEmpty()) {
            return symbols;
        }
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(symbols);
        all.addAll(fxSymbols);
        return List.copyOf(all);
    }
}
