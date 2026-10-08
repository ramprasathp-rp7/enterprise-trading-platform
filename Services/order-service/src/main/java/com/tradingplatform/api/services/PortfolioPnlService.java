package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.dto.PortfolioSummary;
import com.tradingplatform.domain.dto.PricedPosition;
import com.tradingplatform.domain.dto.PnlResponse;
import com.tradingplatform.domain.dto.SymbolPnl;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.DomainException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portfolio and P&L service.
 * Coordinates pricing, position valuation, and P&L calculation.
 * Implements fallback logic: try Fauxnance first, fall back to market-data cache.
 *
 * Multi-currency: each position is valued in its instrument's currency and converted into the
 * account's currency at the live FX rate (FX: pairs quoted in the same batch as the instruments).
 * Totals are sums of the account-currency figures, so they are expressed in baseCurrency as the
 * contract requires. A position with no FX rate is left out of the totals and marks them partial.
 */
@Service
public class PortfolioPnlService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPnlService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final QuoteResolver quoteResolver;
    private final RealisedPnlCalculator realisedPnlCalculator;

    public PortfolioPnlService(AccountMapper accountMapper, InstrumentMapper instrumentMapper,
                               QuoteResolver quoteResolver, RealisedPnlCalculator realisedPnlCalculator) {
        this.accountMapper = Objects.requireNonNull(accountMapper, "accountMapper cannot be null");
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper, "instrumentMapper cannot be null");
        this.quoteResolver = Objects.requireNonNull(quoteResolver, "quoteResolver cannot be null");
        this.realisedPnlCalculator = Objects.requireNonNull(realisedPnlCalculator, "realisedPnlCalculator cannot be null");
    }

    /**
     * Get portfolio summary for an account.
     * Cash, total market value, total cost basis, and P&L totals, all in the account's currency.
     *
     * @param accountId Account ID
     * @return Portfolio summary
     * @throws AccountNotFoundException if the account does not exist (ACC-404)
     */
    public PortfolioSummary getPortfolioSummary(Long accountId) {
        Account account = getAccountOrThrow404(accountId);
        List<Position> positions = getPositionsForAccount(accountId);

        // Get priced positions
        List<PricedPosition> pricedPositions = priceAll(account, positions);

        // Aggregate metrics, in account currency
        BigDecimal marketValue = ZERO;
        BigDecimal costBasis = ZERO;
        boolean partial = false;

        for (PricedPosition pos : pricedPositions) {
            // Cost basis in account currency is known for every holding (it is built at fill rates)
            costBasis = costBasis.add(pos.getCostBasisAccount());
            if (pos.getMarketValueAccount() != null) {
                marketValue = marketValue.add(pos.getMarketValueAccount());
            } else {
                // No price or no FX rate: leave the holding out rather than sum it in the wrong currency
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

        // Get realised P&L (booked in account currency at each SELL fill)
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
     * Each position includes market value, cost basis, and unrealised P&L, in the instrument's
     * currency and converted into the account's.
     *
     * @param accountId Account ID
     * @param symbol Optional filter to one symbol
     * @return List of priced positions
     * @throws AccountNotFoundException if the account does not exist (ACC-404)
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

        return priceAll(account, positions);
    }

    /**
     * Prices positions in one quote batch: the instruments plus the FX pairs that convert their
     * currencies into the account's.
     */
    private List<PricedPosition> priceAll(Account account, List<Position> positions) {
        if (positions.isEmpty()) {
            return new ArrayList<>();
        }
        String accountCurrency = account.getCurrency();

        Map<Long, Instrument> instruments = new HashMap<>();
        Set<String> symbols = new LinkedHashSet<>();
        for (Position pos : positions) {
            Instrument instrument = instrumentMapper.findById(pos.getInstrumentId());
            if (instrument == null) {
                log.warn("Instrument {} not found", pos.getInstrumentId());
                continue;
            }
            instruments.put(pos.getInstrumentId(), instrument);
            symbols.add(instrument.getSymbol());
            symbols.addAll(FxRateService.pairSymbols(instrument.getCurrency(), accountCurrency));
        }

        Map<String, QuoteResolver.ResolvedQuote> quotes = quoteResolver.resolve(symbols);

        // Build priced positions
        List<PricedPosition> result = new ArrayList<>();
        for (Position pos : positions) {
            Instrument instrument = instruments.get(pos.getInstrumentId());
            if (instrument == null) {
                continue;
            }
            result.add(price(account.getId(), accountCurrency, pos, instrument, quotes));
        }
        return result;
    }

    private PricedPosition price(Long accountId, String accountCurrency, Position pos, Instrument instrument,
                                 Map<String, QuoteResolver.ResolvedQuote> quotes) {
        String instrumentSymbol = instrument.getSymbol();
        QuoteResolver.ResolvedQuote qai = quotes.get(instrumentSymbol);
        FauxnanceApiClient.QuoteData quote = qai != null ? qai.quote() : null;

        // Fauxnance is the source of truth for the trading currency; fall back to the instruments table
        String currency = instrumentCurrency(instrument, quote);
        Optional<FxRateService.FxRate> fx = FxRateService.fromQuotes(currency, accountCurrency, quotes);
        if (fx.isEmpty() && !currency.equalsIgnoreCase(instrument.getCurrency())) {
            // The quote's currency was not the one the batch fetched pairs for
            fx = FxRateService.fromQuotes(currency, accountCurrency,
                quoteResolver.resolve(FxRateService.pairSymbols(currency, accountCurrency)));
        }

        BigDecimal quantity = BigDecimal.valueOf(pos.getQuantity());
        BigDecimal costBasis = pos.getAverageCost().multiply(quantity);
        BigDecimal costBasisAccount = pos.getAverageCostAccount().multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal marketValue = null;
        BigDecimal unrealisedPnl = null;
        BigDecimal unrealisedPnlPercent = null;
        BigDecimal marketValueAccount = null;
        BigDecimal unrealisedPnlAccount = null;
        Instant priceAsOf = null;
        boolean stale = qai != null && qai.stale();

        if (quote != null) {
            BigDecimal lastPrice = quote.getPrice();
            marketValue = lastPrice.multiply(quantity);
            unrealisedPnl = marketValue.subtract(costBasis);
            if (costBasis.signum() > 0) {
                unrealisedPnlPercent = unrealisedPnl.divide(costBasis, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            priceAsOf = quote.getQuoteAsOf();
            if (fx.isPresent()) {
                marketValueAccount = marketValue.multiply(fx.get().rate()).setScale(2, RoundingMode.HALF_UP);
                unrealisedPnlAccount = marketValueAccount.subtract(costBasisAccount);
            }
        } else {
            stale = true;  // No price available
        }
        if (fx.isEmpty()) {
            stale = true;  // No FX rate: the holding cannot be expressed in account currency
        } else if (fx.get().stale()) {
            stale = true;
        }

        return new PricedPosition(
                accountId,
                instrumentSymbol,
                pos.getQuantity(),
                pos.getAverageCost(),
                costBasis,
                quote != null ? quote.getPrice() : null,
                marketValue,
                unrealisedPnl,
                unrealisedPnlPercent,
                currency,
                priceAsOf,
                stale,
                fx.map(FxRateService.FxRate::rate).orElse(null),
                pos.getAverageCostAccount(),
                costBasisAccount,
                marketValueAccount,
                unrealisedPnlAccount
        );
    }

    private static String instrumentCurrency(Instrument instrument, FauxnanceApiClient.QuoteData quote) {
        String quoted = quote != null ? quote.getCurrency() : null;
        if (quoted == null || !quoted.trim().matches("[A-Za-z]{3}")) {
            return instrument.getCurrency();
        }
        if (!quoted.trim().equalsIgnoreCase(instrument.getCurrency())) {
            log.warn("Quote currency {} differs from instrument currency {} for {}; using the quote's",
                quoted, instrument.getCurrency(), instrument.getSymbol());
        }
        return quoted.trim();
    }

    /**
     * Get P&L for an account, in the account's currency.
     *
     * @param accountId Account ID
     * @param from Optional lower bound (inclusive)
     * @param to Optional upper bound (inclusive)
     * @param bySymbol Whether to include per-symbol breakdown
     * @return P&L response
     * @throws AccountNotFoundException if the account does not exist (ACC-404)
     * @throws PortfolioPnlException if from is after to (VAL-422)
     */
    public PnlResponse getPnl(Long accountId, LocalDate from, LocalDate to, boolean bySymbol) {
        Account account = getAccountOrThrow404(accountId);

        // Validate date range
        if (from != null && to != null && from.isAfter(to)) {
            throw new PortfolioPnlException("from date must not be after to date");
        }

        // Get unrealised P&L (always as at now), converted into account currency
        List<PricedPosition> pricedPositions = priceAll(account, getPositionsForAccount(accountId));

        Map<String, BigDecimal> unrealisedBySymbol = new HashMap<>();
        BigDecimal totalUnrealisedPnl = ZERO;

        for (PricedPosition pos : pricedPositions) {
            if (pos.getUnrealisedPnlAccount() != null) {
                unrealisedBySymbol.put(pos.getSymbol(), pos.getUnrealisedPnlAccount());
                totalUnrealisedPnl = totalUnrealisedPnl.add(pos.getUnrealisedPnlAccount());
            }
        }

        // Get realised P&L (booked in account currency at each SELL fill)
        Map<String, BigDecimal> realisedBySymbol = realisedPnlCalculator.calculateRealisedPnlBySymbol(
                accountId, from, to, null);
        BigDecimal totalRealisedPnl = realisedBySymbol.values().stream()
                .reduce(ZERO, BigDecimal::add);

        // Build by-symbol breakdown if requested
        List<SymbolPnl> symbolPnlList = null;
        if (bySymbol) {
            symbolPnlList = new ArrayList<>();
            for (PricedPosition pos : pricedPositions) {
                String symbol = pos.getSymbol();
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
            throw new AccountNotFoundException(accountId);
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
     * Invalid portfolio P&L request (VAL-422, 422), handled by GlobalExceptionHandler.
     * An unknown account is AccountNotFoundException (ACC-404, 404).
     */
    public static final class PortfolioPnlException extends DomainException {
        public PortfolioPnlException(String message) {
            super(ErrorCode.VAL_422, message);
        }
    }
}
