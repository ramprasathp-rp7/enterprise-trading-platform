package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.mappers.AccountHistoryMapper;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.domain.dto.AccountHistorySnapshotResponse;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AccountHistoryScheduler generates end-of-day account snapshots.
 * 
 * **Purpose:**
 * Captures the state of each active account at end-of-day for compliance reporting,
 * performance attribution, and portfolio analytics. One row per account per snapshot date.
 * 
 * **Schedule:**
 * - Default: Daily at 23:59 UTC (configurable via `scheduler.account-history.cron`)
 * - Disable the nightly run with `scheduler.account-history.enabled=false` (manual trigger still works)
 * - Manual trigger: POST /api/v1/admin/account-history/snapshot (admin only)
 * - Executes after market close to capture final daily state
 * 
 * **Processing:**
 * For each active account:
 * 1. Retrieve cash_balance from accounts table
 * 2. Retrieve positions (holdings) from positions table
 * 3. Price each holding using fallback logic: market-data cache → Fauxnance API → average cost
 * 4. Calculate networth = cash_balance + sum(quantity × price)
 * 5. Insert record into account_history table with snapshot_date = today (EOD)
 * 
 * **Error Handling:**
 * - Account failures are logged but do NOT block other accounts
 * - If a symbol cannot be priced from the cache or Fauxnance, its average cost is used (logged as a warning)
 * - Skips accounts with no positions (snapshot still created with cash balance as networth)
 * 
 * **Transactionality:**
 * Each snapshot is a single auto-committed INSERT, so one failed account does not roll back the others.
 * Inserts are upserts on (account_id, snapshot_date), so re-running on the same day is safe.
 * 
 * **Dependencies:**
 * - MarketDataCache: latest prices, refreshed by MarketDataRefreshScheduler (primary)
 * - FauxnanceApiClient: only for symbols missing from the cache (saves API quota)
 * - Mappers: AccountMapper, PositionMapper, AccountHistoryMapper
 * 
 * **Related:**
 * - account_history table: stores snapshots (account_id, snapshot_date, cash_balance, networth)
 * - PortfolioPnlService: real-time portfolio valuation (similar logic)
 */
@Service
public class AccountHistoryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AccountHistoryScheduler.class);

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final InstrumentMapper instrumentMapper;
    private final AccountHistoryMapper accountHistoryMapper;
    private final FauxnanceApiClient fauxnanceClient;
    private final MarketDataCache marketDataCache;

    @Value("${scheduler.account-history.enabled:true}")
    private boolean scheduleEnabled = true;

    public AccountHistoryScheduler(
            AccountMapper accountMapper,
            PositionMapper positionMapper,
            InstrumentMapper instrumentMapper,
            AccountHistoryMapper accountHistoryMapper,
            FauxnanceApiClient fauxnanceClient,
            MarketDataCache marketDataCache) {
        this.accountMapper = Objects.requireNonNull(accountMapper, "accountMapper cannot be null");
        this.positionMapper = Objects.requireNonNull(positionMapper, "positionMapper cannot be null");
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper, "instrumentMapper cannot be null");
        this.accountHistoryMapper = Objects.requireNonNull(accountHistoryMapper, "accountHistoryMapper cannot be null");
        this.fauxnanceClient = Objects.requireNonNull(fauxnanceClient, "fauxnanceClient cannot be null");
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
    }

    /**
     * Scheduled task to capture daily account history snapshots.
     * Runs daily at 23:59 UTC (configurable).
     * 
     * Cron expression: "0 59 23 * * *" (23:59 daily)
     * Can be overridden via application.properties: scheduler.account-history.cron
     */
    @Scheduled(cron = "${scheduler.account-history.cron:0 59 23 * * *}", zone = "UTC")
    public void scheduledEndOfDayCapture() {
        if (!scheduleEnabled) {
            logger.info("Scheduled account history capture is disabled (scheduler.account-history.enabled=false). Skipping.");
            return;
        }
        captureEndOfDayAccountHistory();
    }

    /**
     * Capture a snapshot of every active account, stamped with today's 23:59:59 UTC.
     * Safe to run more than once a day: each run overwrites that day's rows.
     *
     * @return Summary of the run
     */
    public AccountHistorySnapshotResponse captureEndOfDayAccountHistory() {
        logger.info("Starting end-of-day account history capture...");
        
        Instant startTime = Instant.now();
        // Computed once so a run that crosses midnight stamps every account with the same date
        Instant snapshotDate = getEndOfDaySnapshot(startTime);
        int totalAccounts = 0;
        int successCount = 0;
        int failureCount = 0;

        try {
            // Step 1: Get all active accounts
            List<Account> activeAccounts = accountMapper.findAllActiveAccounts();
            totalAccounts = activeAccounts.size();
            
            logger.info("Found {} active accounts for snapshot", totalAccounts);

            // Step 2: Process each account
            for (Account account : activeAccounts) {
                try {
                    snapshotAccount(account, snapshotDate);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    logger.error("Failed to snapshot account id={}, account_id={}: {}", 
                            account.getId(), account.getAccountId(), e.getMessage(), e);
                    // Continue processing other accounts
                }
            }

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
            
            logger.info("End-of-day account history capture completed. Total={}, Success={}, Failure={}, Duration={}ms",
                    totalAccounts, successCount, failureCount, durationMs);

            return new AccountHistorySnapshotResponse(snapshotDate, totalAccounts, successCount, failureCount);
        } catch (Exception e) {
            logger.error("Fatal error in account history capture: {}", e.getMessage(), e);
            throw new RuntimeException("Account history capture failed", e);
        }
    }

    /**
     * Snapshot a single account's state at end-of-day.
     * 
     * @param account Account to snapshot
     * @param snapshotDate End-of-day timestamp shared by every account in this run
     */
    private void snapshotAccount(Account account, Instant snapshotDate) {
        // Step 1: Get current cash balance
        BigDecimal cashBalance = account.getCashBalance();
        
        // Step 2: Get positions for this account
        List<Position> positions = positionMapper.findByAccountId(account.getId());
        
        // Step 3: Price each position and calculate market value
        BigDecimal marketValue = calculateMarketValue(positions);
        
        // Step 4: Calculate networth = cash + market value
        BigDecimal networth = cashBalance.add(marketValue);
        
        // Step 5: Upsert into account_history
        int inserted = accountHistoryMapper.insertAccountHistory(
                account.getId(),
                snapshotDate,
                cashBalance,
                networth
        );
        
        if (inserted == 1) {
            logger.debug("Snapshot created: accountId={}, date={}, cash={}, networth={}",
                    account.getId(), snapshotDate, cashBalance, networth);
        } else {
            throw new RuntimeException("Failed to insert account history snapshot for account id=" + account.getId());
        }
    }

    /**
     * Calculate total market value of all positions.
     * Pricing order: market-data cache (kept fresh by the poller) → Fauxnance API (only for
     * symbols the cache is missing) → the position's average cost as a last resort.
     *
     * @param positions List of positions to price
     * @return Total market value (sum of quantity × price for all positions)
     */
    private BigDecimal calculateMarketValue(List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Resolve symbols for non-zero positions and price them from the cache where possible
        Map<Position, String> symbolsByPosition = new LinkedHashMap<>();
        Map<String, BigDecimal> pricesBySymbol = new HashMap<>();
        List<String> uncachedSymbols = new ArrayList<>();

        for (Position position : positions) {
            if (position.getQuantity() <= 0) { // Skip zero-quantity positions
                continue;
            }
            Instrument instrument = instrumentMapper.findById(position.getInstrumentId());
            String symbol = instrument != null ? instrument.getSymbol() : null;
            symbolsByPosition.put(position, symbol);
            if (symbol == null || pricesBySymbol.containsKey(symbol) || uncachedSymbols.contains(symbol)) {
                continue;
            }
            marketDataCache.getLatestQuote(symbol)
                    .map(QuotePayload::price)
                    .ifPresentOrElse(price -> pricesBySymbol.put(symbol, price), () -> uncachedSymbols.add(symbol));
        }

        // Only spend Fauxnance quota on symbols the cache could not price
        if (!uncachedSymbols.isEmpty()) {
            logger.debug("Cache missing prices for {}. Fetching from Fauxnance.", uncachedSymbols);
            fauxnanceClient.getBatchedQuotes(uncachedSymbols).forEach((symbol, quote) -> {
                if (quote != null && quote.getPrice() != null) {
                    pricesBySymbol.put(symbol, quote.getPrice());
                }
            });
        }

        BigDecimal marketValue = BigDecimal.ZERO;
        for (Map.Entry<Position, String> entry : symbolsByPosition.entrySet()) {
            Position position = entry.getKey();
            String symbol = entry.getValue();

            BigDecimal price = symbol != null ? pricesBySymbol.get(symbol) : null;
            if (price == null) {
                // Better an approximate networth than a permanently understated one
                price = position.getAverageCost();
                logger.warn("No market price for symbol={} (instrumentId={}). Using average cost {} for snapshot.",
                        symbol, position.getInstrumentId(), price);
            }

            // Market value = quantity × price
            marketValue = marketValue.add(price.multiply(BigDecimal.valueOf(position.getQuantity())));
        }

        return marketValue;
    }

    /**
     * Get end-of-day snapshot timestamp: the UTC date of {@code runStart} at 23:59:59 UTC.
     *
     * @param runStart When the snapshot run started
     * @return Instant representing end-of-day
     */
    private Instant getEndOfDaySnapshot(Instant runStart) {
        LocalDate day = LocalDate.ofInstant(runStart, ZoneOffset.UTC);
        return day.atTime(LocalTime.of(23, 59, 59)).toInstant(ZoneOffset.UTC);
    }

    /**
     * Manually trigger account history snapshot (for testing/admin).
     * Same logic as scheduled task, but callable on-demand and regardless of
     * scheduler.account-history.enabled.
     *
     * @return Summary of the run
     */
    public AccountHistorySnapshotResponse triggerManualSnapshot() {
        logger.info("Manual account history snapshot triggered");
        return captureEndOfDayAccountHistory();
    }
}
