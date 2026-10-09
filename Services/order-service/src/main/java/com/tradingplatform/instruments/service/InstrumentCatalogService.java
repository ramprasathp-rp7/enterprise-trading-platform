package com.tradingplatform.instruments.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.services.CandleSyncService;
import com.tradingplatform.api.services.FauxnanceApiClient;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.exception.MarketDataUnavailableException;
import com.tradingplatform.domain.exception.SymbolNotDiscoveredException;
import com.tradingplatform.instruments.InstrumentExchanges;
import com.tradingplatform.instruments.model.InstrumentListing;

/**
 * The instrument catalogue, and symbol discovery: adding an instrument the platform does not list
 * yet when Fauxnance knows the symbol and can quote it.
 *
 * Discovery spends two Fauxnance requests (the registry lookup and one quote), and only for a
 * symbol that is not already listed. Once added, the instrument is an ordinary row: the scheduled
 * quote refresh and the executor's poller quote every symbol in the table, so it stays priced, and
 * its first quote goes into the cache straight away. Its daily candles are loaded in the background.
 */
@Service
public class InstrumentCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(InstrumentCatalogService.class);

    /** Letters, digits and the separators Fauxnance symbols use (FX:EURUSD, X:BTC-USD, INFY.NS, BRK-B) */
    static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9][A-Z0-9.:\\-^=]{0,19}$");
    /** Daily candles loaded for a newly discovered instrument, like the start-up bootstrap */
    static final int CANDLE_MONTHS = 3;

    private final InstrumentMapper instrumentMapper;
    private final FauxnanceApiClient fauxnance;
    private final MarketDataCache marketDataCache;
    private final CandleSyncService candleSyncService;
    private final Executor backgroundExecutor;
    private final Clock clock;

    @Autowired
    public InstrumentCatalogService(InstrumentMapper instrumentMapper, FauxnanceApiClient fauxnance,
                                    MarketDataCache marketDataCache, CandleSyncService candleSyncService) {
        this(instrumentMapper, fauxnance, marketDataCache, candleSyncService,
            java.util.concurrent.ForkJoinPool.commonPool(), Clock.systemUTC());
    }

    InstrumentCatalogService(InstrumentMapper instrumentMapper, FauxnanceApiClient fauxnance,
                             MarketDataCache marketDataCache, CandleSyncService candleSyncService,
                             Executor backgroundExecutor, Clock clock) {
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper);
        this.fauxnance = Objects.requireNonNull(fauxnance);
        this.marketDataCache = Objects.requireNonNull(marketDataCache);
        this.candleSyncService = Objects.requireNonNull(candleSyncService);
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Every listed instrument, delisted included, with its exchange resolved */
    public List<InstrumentListing> list() {
        List<InstrumentListing> all = instrumentMapper.findAllListings();
        all.forEach(i -> i.setExchange(InstrumentExchanges.resolve(i.getSymbol(), i.getExchange())));
        return all;
    }

    /** The outcome of a discovery: the instrument, and whether this call added it */
    public record Discovery(InstrumentListing instrument, boolean created) {
    }

    /**
     * Returns the listed instrument for the symbol, adding it first if Fauxnance knows it.
     *
     * @throws InvalidOrderException (VAL-422) for something that cannot be a symbol
     * @throws SymbolNotDiscoveredException (INS-404) when Fauxnance does not know the symbol or cannot quote it
     * @throws MarketDataUnavailableException (MKT-503) when Fauxnance cannot be reached
     */
    public Discovery discover(String rawSymbol) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(symbol).matches()) {
            throw new InvalidOrderException("Enter a symbol such as AAPL, INFY.NS, FX:EURUSD or X:BTC-USD");
        }

        InstrumentListing existing = instrumentMapper.findListingBySymbol(symbol);
        if (existing != null) {
            existing.setExchange(InstrumentExchanges.resolve(existing.getSymbol(), existing.getExchange()));
            return new Discovery(existing, false);
        }

        FauxnanceApiClient.SymbolInfo info;
        Map<String, FauxnanceApiClient.QuoteData> quotes;
        try {
            Optional<FauxnanceApiClient.SymbolInfo> found = fauxnance.findSymbol(symbol);
            if (found.isEmpty()) {
                throw new SymbolNotDiscoveredException(symbol, "No market data was found for " + symbol + ".");
            }
            info = found.get();
            quotes = fauxnance.getBatchedQuotes(List.of(symbol));
        } catch (SymbolNotDiscoveredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MarketDataUnavailableException("Symbol discovery failed for " + symbol, ex);
        }
        FauxnanceApiClient.QuoteData quote = quotes.get(symbol);
        if (quote == null || quote.getPrice() == null) {
            throw new SymbolNotDiscoveredException(symbol, symbol + " is known but has no price yet. Try again later.");
        }

        InstrumentListing instrument = new InstrumentListing(
            null,
            symbol,
            truncate(info.name(), 120),
            assetClass(info.type(), symbol),
            currency(info.currency(), quote.getCurrency()),
            info.active(),
            InstrumentExchanges.fromRegistry(symbol, info.exchange()));
        try {
            instrumentMapper.insertDiscovered(instrument);
        } catch (DuplicateKeyException ex) {
            // Someone else discovered it at the same moment: theirs is the row
            InstrumentListing winner = instrumentMapper.findListingBySymbol(symbol);
            winner.setExchange(InstrumentExchanges.resolve(winner.getSymbol(), winner.getExchange()));
            return new Discovery(winner, false);
        }

        marketDataCache.updateQuote(symbol, quote.toPayload(), Instant.now(clock));
        loadCandlesInBackground(instrument);
        logger.info("Instrument discovered: symbol={}, name={}, exchange={}, currency={}, tradable={}",
            symbol, instrument.getName(), instrument.getExchange(), instrument.getCurrency(), instrument.isTradable());
        return new Discovery(instrument, true);
    }

    private void loadCandlesInBackground(InstrumentListing instrument) {
        LocalDate today = LocalDate.now(clock);
        backgroundExecutor.execute(() -> {
            try {
                candleSyncService.bootstrapCandleData(instrument.getId(), instrument.getSymbol(),
                    today.minusMonths(CANDLE_MONTHS), today.minusDays(1));
            } catch (RuntimeException ex) {
                // The nightly candle sync retries failed bootstraps
                logger.warn("Candle bootstrap failed for discovered instrument {}: {}", instrument.getSymbol(), ex.getMessage());
            }
        });
    }

    /** equity and etf trade as EQUITY; fx and crypto as themselves; the symbol prefix decides otherwise */
    static String assetClass(String registryType, String symbol) {
        String type = registryType == null ? "" : registryType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "fx" -> "FX";
            case "crypto" -> "CRYPTO";
            case "equity", "etf" -> "EQUITY";
            default -> symbol.startsWith("FX:") ? "FX" : symbol.startsWith("X:") ? "CRYPTO" : "EQUITY";
        };
    }

    /** The registry's currency, else the quote's, else USD; always three upper-case letters */
    static String currency(String registryCurrency, String quoteCurrency) {
        for (String candidate : new String[] {registryCurrency, quoteCurrency}) {
            if (candidate != null && candidate.trim().matches("[A-Za-z]{3}")) {
                return candidate.trim().toUpperCase(Locale.ROOT);
            }
        }
        return "USD";
    }

    private static String truncate(String value, int max) {
        String v = value == null || value.isBlank() ? "Unknown instrument" : value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
