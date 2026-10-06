package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.domain.dto.PortfolioSummary;
import com.tradingplatform.domain.dto.PricedPosition;
import com.tradingplatform.domain.dto.PnlResponse;
import com.tradingplatform.domain.dto.SymbolPnl;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Portfolio and P&L service.
 * Coordinates pricing, position valuation, and P&L calculation.
 * Implements fallback logic: try Fauxnance first, fall back to market-data cache.
 */
@Service
public class PortfolioPnlService {
    
    private static final Logger log = LoggerFactory.getLogger(PortfolioPnlService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final FauxnanceApiClient fauxnanceClient;
    private final MarketDataCache marketDataCache;
    private final RealisedPnlCalculator realisedPnlCalculator;

    public PortfolioPnlService(AccountMapper accountMapper, InstrumentMapper instrumentMapper,
                               FauxnanceApiClient fauxnanceClient,
                               MarketDataCache marketDataCache, RealisedPnlCalculator realisedPnlCalculator) {
        this.accountMapper = Objects.requireNonNull(accountMapper, "accountMapper cannot be null");
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper, "instrumentMapper cannot be null");
        this.fauxnanceClient = Objects.requireNonNull(fauxnanceClient, "fauxnanceClient cannot be null");
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
        this.realisedPnlCalculator = Objects.requireNonNull(realisedPnlCalculator, "realisedPnlCalculator cannot be null");
    }

    /**
     * Get portfolio summary for an account.
     * Cash, total market value, total cost basis, and P&L totals.
     * 
     * @param accountId Account ID
     * @return Portfolio summary
     * @throws PortfolioPnlException on errors (404, 503)
     */
    public PortfolioSummary getPortfolioSummary(Long accountId) {
        Account account = getAccountOrThrow404(accountId);
        List<Position> positions = getPositionsForAccount(accountId);
        
        // Get priced positions
        List<PricedPosition> pricedPositions = getPricedPositions(accountId, positions, null);
        
        // Aggregate metrics
        BigDecimal marketValue = ZERO;
        BigDecimal costBasis = ZERO;
        boolean partial = false;

        for (PricedPosition pos : pricedPositions) {
            costBasis = costBasis.add(pos.getCostBasis());
            if (pos.getMarketValue() != null) {
                marketValue = marketValue.add(pos.getMarketValue());
            } else {
                partial = true;
            }
        }

        // Calculate unrealised P&L
        BigDecimal unrealisedPnl = marketValue.subtract(costBasis);
        BigDecimal unrealisedPnlPercent = null;
        if (costBasis.signum() > 0) {
            unrealisedPnlPercent = unrealisedPnl.divide(costBasis, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Get realised P&L
        BigDecimal realisedPnl = realisedPnlCalculator.calculateTotalRealisedPnl(accountId);

        // Calculate totals
        BigDecimal totalValue = account.getCashBalance().add(marketValue);
        Instant asOf = Instant.now();

        return new PortfolioSummary(
                accountId,
                account.getCurrency(),
                account.getCashBalance(),
                marketValue,
                costBasis,
                unrealisedPnl,
                unrealisedPnlPercent,
                realisedPnl,
                totalValue,
                pricedPositions.size(),
                partial,
                asOf
        );
    }

    /**
     * Get priced positions for an account.
     * Each position includes market value, cost basis, and unrealised P&L.
     * 
     * @param accountId Account ID
     * @param symbol Optional filter to one symbol
     * @return List of priced positions
     * @throws PortfolioPnlException on errors (404, 503)
     */
    public List<PricedPosition> getPricedPositions(Long accountId, String symbol) {
        Account account = getAccountOrThrow404(accountId);
        List<Position> positions = getPositionsForAccount(accountId);
        
        // Filter by symbol if provided
        if (symbol != null && !symbol.isEmpty()) {
            positions = positions.stream()
                    .filter(p -> getInstrumentSymbol(p.getInstrumentId()).equals(symbol))
                    .collect(Collectors.toList());
        }

        return getPricedPositions(accountId, positions, symbol);
    }

    /**
     * Internal method to get priced positions.
     */
    private List<PricedPosition> getPricedPositions(Long accountId, List<Position> positions, String filterSymbol) {
        if (positions.isEmpty()) {
            return new ArrayList<>();
        }

        // Extract symbols and get quotes
        List<String> symbols = positions.stream()
                .map(p -> getInstrumentSymbol(p.getInstrumentId()))
                .collect(Collectors.toList());

        Map<String, QuoteAndInstrument> quotes = getQuotesWithFallback(symbols);

        // Build priced positions
        List<PricedPosition> result = new ArrayList<>();
        for (Position pos : positions) {
            String instrumentSymbol = getInstrumentSymbol(pos.getInstrumentId());
            Instrument instrument = instrumentMapper.findById(pos.getInstrumentId());

            if (instrument == null) {
                log.warn("Instrument {} not found", pos.getInstrumentId());
                continue;
            }

            QuoteAndInstrument qai = quotes.getOrDefault(instrumentSymbol, new QuoteAndInstrument(null, null, false));
            
            BigDecimal costBasis = pos.getAverageCost().multiply(BigDecimal.valueOf(pos.getQuantity()));
            BigDecimal marketValue = null;
            BigDecimal unrealisedPnl = null;
            BigDecimal unrealisedPnlPercent = null;
            Instant priceAsOf = null;
            boolean stale = qai.stale;

            if (qai.quote != null) {
                BigDecimal lastPrice = qai.quote.getPrice();
                marketValue = lastPrice.multiply(BigDecimal.valueOf(pos.getQuantity()));
                unrealisedPnl = marketValue.subtract(costBasis);
                if (costBasis.signum() > 0) {
                    unrealisedPnlPercent = unrealisedPnl.divide(costBasis, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
                priceAsOf = qai.quote.getQuoteAsOf();
            } else {
                stale = true;  // No price available
            }

            PricedPosition pricedPos = new PricedPosition(
                    accountId,
                    instrumentSymbol,
                    pos.getQuantity(),
                    pos.getAverageCost(),
                    costBasis,
                    qai.quote != null ? qai.quote.getPrice() : null,
                    marketValue,
                    unrealisedPnl,
                    unrealisedPnlPercent,
                    instrument.getCurrency(),
                    priceAsOf,
                    stale
            );

            result.add(pricedPos);
        }

        return result;
    }

    /**
     * Get P&L for an account.
     * 
     * @param accountId Account ID
     * @param from Optional lower bound (inclusive)
     * @param to Optional upper bound (inclusive)
     * @param bySymbol Whether to include per-symbol breakdown
     * @return P&L response
     * @throws PortfolioPnlException on errors (404, 503)
     */
    public PnlResponse getPnl(Long accountId, LocalDate from, LocalDate to, boolean bySymbol) {
        Account account = getAccountOrThrow404(accountId);
        
        // Validate date range
        if (from != null && to != null && from.isAfter(to)) {
            throw new PortfolioPnlException("VAL-422", "from date must not be after to date");
        }

        // Get unrealised P&L (always as at now)
        List<Position> positions = getPositionsForAccount(accountId);
        List<String> symbols = positions.stream()
                .map(p -> getInstrumentSymbol(p.getInstrumentId()))
                .collect(Collectors.toList());
        Map<String, QuoteAndInstrument> quotes = getQuotesWithFallback(symbols);

        Map<String, BigDecimal> unrealisedBySymbol = new HashMap<>();
        BigDecimal totalUnrealisedPnl = ZERO;

        for (Position pos : positions) {
            String symbol = getInstrumentSymbol(pos.getInstrumentId());
            QuoteAndInstrument qai = quotes.getOrDefault(symbol, new QuoteAndInstrument(null, null, false));

            if (qai.quote != null) {
                BigDecimal costBasis = pos.getAverageCost().multiply(BigDecimal.valueOf(pos.getQuantity()));
                BigDecimal marketValue = qai.quote.getPrice().multiply(BigDecimal.valueOf(pos.getQuantity()));
                BigDecimal unrealised = marketValue.subtract(costBasis);
                unrealisedBySymbol.put(symbol, unrealised);
                totalUnrealisedPnl = totalUnrealisedPnl.add(unrealised);
            }
        }

        // Get realised P&L
        Map<String, BigDecimal> realisedBySymbol = realisedPnlCalculator.calculateRealisedPnlBySymbol(
                accountId, from, to, null);
        BigDecimal totalRealisedPnl = realisedBySymbol.values().stream()
                .reduce(ZERO, BigDecimal::add);

        // Build by-symbol breakdown if requested
        List<SymbolPnl> symbolPnlList = null;
        if (bySymbol) {
            symbolPnlList = new ArrayList<>();
            for (Position pos : positions) {
                String symbol = getInstrumentSymbol(pos.getInstrumentId());
                BigDecimal realised = realisedBySymbol.getOrDefault(symbol, ZERO);
                BigDecimal unrealised = unrealisedBySymbol.getOrDefault(symbol, ZERO);
                BigDecimal total = realised.add(unrealised);
                symbolPnlList.add(new SymbolPnl(symbol, realised, unrealised, total));
            }
        }

        BigDecimal totalPnl = totalRealisedPnl.add(totalUnrealisedPnl);

        return new PnlResponse(
                accountId,
                account.getCurrency(),
                from,
                to,
                totalRealisedPnl,
                totalUnrealisedPnl,
                totalPnl,
                symbolPnlList,
                Instant.now()
        );
    }

    /**
     * Get quotes with fallback logic.
     * 1. Try Fauxnance API
     * 2. Fall back to market-data cache
     */
    private Map<String, QuoteAndInstrument> getQuotesWithFallback(List<String> symbols) {
        Map<String, QuoteAndInstrument> result = new HashMap<>();

        log.info("getQuotesWithFallback called for {} symbols: {}", symbols.size(), symbols);

        // Try Fauxnance first
        try {
            Map<String, FauxnanceApiClient.QuoteData> fauxnanceQuotes = fauxnanceClient.getBatchedQuotes(symbols);
            log.info("Fauxnance returned {} quotes", fauxnanceQuotes.size());
            for (FauxnanceApiClient.QuoteData quote : fauxnanceQuotes.values()) {
                result.put(quote.getSymbol(), new QuoteAndInstrument(quote, quote, quote.isStale()));
                log.debug("Added Fauxnance quote: {} = {}", quote.getSymbol(), quote.getPrice());
            }
            log.debug("Got {} quotes from Fauxnance", fauxnanceQuotes.size());
        } catch (Exception ex) {
            log.warn("Fauxnance API failed, falling back to cache: {}", ex.getMessage());
        }

        // Fall back to cache for symbols not in Fauxnance result
        for (String symbol : symbols) {
            if (!result.containsKey(symbol)) {
                log.debug("Symbol {} not in Fauxnance results, checking cache", symbol);
                Optional<com.tradingplatform.api.events.QuotePayload> cachedQuote = marketDataCache.getLatestQuote(symbol);
                if (cachedQuote.isPresent()) {
                    com.tradingplatform.api.events.QuotePayload quote = cachedQuote.get();
                    // Convert QuotePayload to QuoteData
                    FauxnanceApiClient.QuoteData qd = new FauxnanceApiClient.QuoteData(
                            quote.symbol(),
                            quote.price(),
                            quote.bid(),
                            quote.ask(),
                            quote.currency(),
                            quote.quoteAsOf(),
                            quote.stale() != null && quote.stale()
                    );
                    result.put(symbol, new QuoteAndInstrument(qd, quote, true));  // Mark as stale from cache
                    log.debug("Using cached quote for {}: {}", symbol, quote.price());
                } else {
                    log.warn("No quote available for symbol {}", symbol);
                }
            }
        }

        log.info("getQuotesWithFallback returning {} total quotes", result.size());
        return result;
    }

    /**
     * Get positions for an account (non-zero only).
     */
    private List<Position> getPositionsForAccount(Long accountId) {
        List<Position> positions = accountMapper.findPositionByAccountId(accountId);
        if (positions == null) {
            positions = new ArrayList<>();
        }
        // Filter out positions with zero quantity
        return positions.stream()
                .filter(p -> p.getQuantity() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Get account or throw 404.
     */
    private Account getAccountOrThrow404(Long accountId) {
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new PortfolioPnlException("ACC-404", "Account not found");
        }
        return account;
    }

    /**
     * Get instrument symbol by ID (cached per position).
     */
    private String getInstrumentSymbol(Long instrumentId) {
        Instrument instrument = instrumentMapper.findById(instrumentId);
        return instrument != null ? instrument.getSymbol() : "UNKNOWN";
    }

    /**
     * Internal wrapper for quote data.
     */
    private static final class QuoteAndInstrument {
        private final FauxnanceApiClient.QuoteData quote;
        private final Object original;
        private final boolean stale;

        QuoteAndInstrument(FauxnanceApiClient.QuoteData quote, Object original, boolean stale) {
            this.quote = quote;
            this.original = original;
            this.stale = stale;
        }
    }

    /**
     * Portfolio P&L exception.
     */
    public static final class PortfolioPnlException extends RuntimeException {
        private final String errorCode;

        public PortfolioPnlException(String errorCode, String message) {
            super(message);
            this.errorCode = Objects.requireNonNull(errorCode);
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
