package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.mappers.AccountHistoryMapper;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.domain.dto.AccountHistorySnapshotResponse;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountHistoryScheduler.
 * 
 * Tests cover:
 * 1. End-of-day snapshot creation
 * 2. Market value calculation (cache first, Fauxnance for cache misses)
 * 3. Average-cost fallback when no price is available
 * 4. Error handling and continuation
 * 5. Multiple accounts processing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountHistoryScheduler Tests")
class AccountHistorySchedulerTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private PositionMapper positionMapper;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private AccountHistoryMapper accountHistoryMapper;

    @Mock
    private FauxnanceApiClient fauxnanceClient;

    @Mock
    private MarketDataCache marketDataCache;

    @InjectMocks
    private AccountHistoryScheduler scheduler;

    private Account activeAccount1;
    private Account activeAccount2;
    private Instrument instrument1;
    private Instrument instrument2;
    private Position position1;
    private Position position2;

    @BeforeEach
    void setUp() {
        // Setup test accounts
        activeAccount1 = new Account(
                1L, "ACC-001", "John Doe", 
                BigDecimal.valueOf(10000), "USD", AccountStatus.ACTIVE, 
                1, Instant.now(), Instant.now()
        );
        
        activeAccount2 = new Account(
                2L, "ACC-002", "Jane Smith", 
                BigDecimal.valueOf(20000), "USD", AccountStatus.ACTIVE, 
                1, Instant.now(), Instant.now()
        );

        // Setup test instruments
        instrument1 = new Instrument(1L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        instrument2 = new Instrument(2L, "GOOGL", "Alphabet Inc.", AssetClass.EQUITY, "USD", true);

        // Setup test positions
        position1 = new Position(1L, 1L, 10, BigDecimal.valueOf(150), 0);
        position2 = new Position(1L, 2L, 5, BigDecimal.valueOf(2800), 0);
    }

    @Test
    @DisplayName("Should capture end-of-day snapshot for single account with positions")
    void testCaptureEndOfDayAccountHistoryWithPositions() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1);
        List<Position> positions = List.of(position1);

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions);
        when(instrumentMapper.findById(1L)).thenReturn(instrument1);

        FauxnanceApiClient.QuoteData quote = new FauxnanceApiClient.QuoteData(
                "AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(174.5), 
                BigDecimal.valueOf(175.5), "USD", Instant.now(), false
        );
        Map<String, FauxnanceApiClient.QuoteData> quotes = new HashMap<>();
        quotes.put("AAPL", quote);

        when(fauxnanceClient.getBatchedQuotes(List.of("AAPL"))).thenReturn(quotes);
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        verify(accountMapper, times(1)).findAllActiveAccounts();
        verify(positionMapper, times(1)).findByAccountId(1L);
        verify(fauxnanceClient, times(1)).getBatchedQuotes(List.of("AAPL"));
        
        // Verify insert called with correct values
        // networth = cash (10000) + position value (10 * 175 = 1750) = 11750
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)), 
                        eq(BigDecimal.valueOf(11750).setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("Should handle account with no positions")
    void testCaptureEndOfDayAccountHistoryNoPositions() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1);
        List<Position> positions = List.of();

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions);
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        // networth = cash only (10000) since no positions
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("Should price from market-data cache without calling Fauxnance")
    void testPricesFromCacheFirst() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1);
        List<Position> positions = List.of(position1);

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions);
        when(instrumentMapper.findById(1L)).thenReturn(instrument1);

        // Cache has the quote
        QuotePayload cachedQuote = new QuotePayload("AAPL", BigDecimal.valueOf(170), BigDecimal.valueOf(169.5), 
                BigDecimal.valueOf(170.5), "USD", BigDecimal.valueOf(2), BigDecimal.valueOf(1.19), 
                BigDecimal.valueOf(168), "open", false, Instant.now());
        when(marketDataCache.getLatestQuote("AAPL")).thenReturn(Optional.of(cachedQuote));

        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        verify(fauxnanceClient, never()).getBatchedQuotes(anyList());
        // networth = cash (10000) + position value (10 * 170 = 1700) = 11700
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)), 
                        eq(BigDecimal.valueOf(11700).setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("Should process multiple accounts")
    void testCaptureMultipleAccounts() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1, activeAccount2);
        List<Position> positions1 = List.of(position1);
        List<Position> positions2 = List.of(position2);

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions1);
        when(positionMapper.findByAccountId(2L)).thenReturn(positions2);
        when(instrumentMapper.findById(1L)).thenReturn(instrument1);
        when(instrumentMapper.findById(2L)).thenReturn(instrument2);

        FauxnanceApiClient.QuoteData quote1 = new FauxnanceApiClient.QuoteData(
                "AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(174.5), 
                BigDecimal.valueOf(175.5), "USD", Instant.now(), false
        );
        FauxnanceApiClient.QuoteData quote2 = new FauxnanceApiClient.QuoteData(
                "GOOGL", BigDecimal.valueOf(140), BigDecimal.valueOf(139.5), 
                BigDecimal.valueOf(140.5), "USD", Instant.now(), false
        );

        Map<String, FauxnanceApiClient.QuoteData> quotes1 = Map.of("AAPL", quote1);
        Map<String, FauxnanceApiClient.QuoteData> quotes2 = Map.of("GOOGL", quote2);

        when(fauxnanceClient.getBatchedQuotes(List.of("AAPL"))).thenReturn(quotes1);
        when(fauxnanceClient.getBatchedQuotes(List.of("GOOGL"))).thenReturn(quotes2);

        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        verify(accountHistoryMapper, times(2))
                .insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should skip zero-quantity positions")
    void testSkipZeroQuantityPositions() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1);
        Position zeroPosition = new Position(1L, 1L, 0, BigDecimal.ZERO, 0); // Zero quantity
        List<Position> positions = List.of(zeroPosition);

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions);
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        // Should not call fauxnanceClient since position quantity is 0
        verify(fauxnanceClient, never()).getBatchedQuotes(anyList());
        
        // networth = cash only (10000)
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("Should continue on single account failure")
    void testContinueOnAccountFailure() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1, activeAccount2);
        List<Position> positions = List.of();

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenThrow(new RuntimeException("Database error"));
        when(positionMapper.findByAccountId(2L)).thenReturn(positions);
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        // Should insert for account 2 even though account 1 failed
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(2L), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should calculate correct networth with multiple positions")
    void testCalculateNetworthMultiplePositions() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1);
        List<Position> positions = List.of(position1, position2); // 2 positions

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions);
        when(instrumentMapper.findById(1L)).thenReturn(instrument1);
        when(instrumentMapper.findById(2L)).thenReturn(instrument2);

        FauxnanceApiClient.QuoteData quote1 = new FauxnanceApiClient.QuoteData(
                "AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(174.5), 
                BigDecimal.valueOf(175.5), "USD", Instant.now(), false
        );
        FauxnanceApiClient.QuoteData quote2 = new FauxnanceApiClient.QuoteData(
                "GOOGL", BigDecimal.valueOf(140), BigDecimal.valueOf(139.5), 
                BigDecimal.valueOf(140.5), "USD", Instant.now(), false
        );

        Map<String, FauxnanceApiClient.QuoteData> quotes = Map.of("AAPL", quote1, "GOOGL", quote2);
        when(fauxnanceClient.getBatchedQuotes(anyList())).thenReturn(quotes);

        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        // networth = cash (10000) + AAPL value (10 * 175) + GOOGL value (5 * 140)
        //          = 10000 + 1750 + 700 = 12450
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)), 
                        eq(BigDecimal.valueOf(12450).setScale(2, RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("Should fall back to average cost when a symbol cannot be priced")
    void testPartialPricingFailure() {
        // Arrange
        List<Account> accounts = List.of(activeAccount1);
        List<Position> positions = List.of(position1, position2); // 2 positions

        when(accountMapper.findAllActiveAccounts()).thenReturn(accounts);
        when(positionMapper.findByAccountId(1L)).thenReturn(positions);
        when(instrumentMapper.findById(1L)).thenReturn(instrument1);
        when(instrumentMapper.findById(2L)).thenReturn(instrument2);

        // Only AAPL priced, GOOGL missing
        FauxnanceApiClient.QuoteData quote1 = new FauxnanceApiClient.QuoteData(
                "AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(174.5), 
                BigDecimal.valueOf(175.5), "USD", Instant.now(), false
        );
        Map<String, FauxnanceApiClient.QuoteData> quotes = Map.of("AAPL", quote1);

        when(fauxnanceClient.getBatchedQuotes(List.of("AAPL", "GOOGL"))).thenReturn(quotes);

        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        // networth = cash (10000) + AAPL value (10 * 175) + GOOGL at average cost (5 * 2800)
        //          = 10000 + 1750 + 14000 = 25750
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class), 
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)), 
                        argThat(nw -> nw.compareTo(BigDecimal.valueOf(25750)) == 0));
    }

    @Test
    @DisplayName("Should handle empty account list")
    void testEmptyAccountList() {
        // Arrange
        when(accountMapper.findAllActiveAccounts()).thenReturn(List.of());

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        verify(accountHistoryMapper, never())
                .insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should stamp every account in a run with the same 23:59:59 UTC snapshot date")
    void testAllAccountsShareEndOfDaySnapshotDate() {
        // Arrange
        when(accountMapper.findAllActiveAccounts()).thenReturn(List.of(activeAccount1, activeAccount2));
        when(positionMapper.findByAccountId(anyLong())).thenReturn(List.of());
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        ArgumentCaptor<Instant> dates = ArgumentCaptor.forClass(Instant.class);
        verify(accountHistoryMapper, times(2))
                .insertAccountHistory(anyLong(), dates.capture(), any(BigDecimal.class), any(BigDecimal.class));

        Instant first = dates.getAllValues().get(0);
        assertEquals(first, dates.getAllValues().get(1));
        assertEquals(LocalTime.of(23, 59, 59), first.atZone(ZoneOffset.UTC).toLocalTime());
        assertEquals(todayUtc, first.atZone(ZoneOffset.UTC).toLocalDate());
    }

    @Test
    @DisplayName("Should only ask Fauxnance for symbols missing from the cache")
    void testFauxnanceOnlyForCacheMisses() {
        // Arrange
        when(accountMapper.findAllActiveAccounts()).thenReturn(List.of(activeAccount1));
        when(positionMapper.findByAccountId(1L)).thenReturn(List.of(position1, position2));
        when(instrumentMapper.findById(1L)).thenReturn(instrument1);
        when(instrumentMapper.findById(2L)).thenReturn(instrument2);

        QuotePayload cachedAapl = new QuotePayload("AAPL", BigDecimal.valueOf(170), BigDecimal.valueOf(169.5),
                BigDecimal.valueOf(170.5), "USD", BigDecimal.valueOf(2), BigDecimal.valueOf(1.19),
                BigDecimal.valueOf(168), "open", false, Instant.now());
        when(marketDataCache.getLatestQuote("AAPL")).thenReturn(Optional.of(cachedAapl));
        when(marketDataCache.getLatestQuote("GOOGL")).thenReturn(Optional.empty());

        FauxnanceApiClient.QuoteData googl = new FauxnanceApiClient.QuoteData(
                "GOOGL", BigDecimal.valueOf(140), BigDecimal.valueOf(139.5),
                BigDecimal.valueOf(140.5), "USD", Instant.now(), false
        );
        when(fauxnanceClient.getBatchedQuotes(List.of("GOOGL"))).thenReturn(Map.of("GOOGL", googl));
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        scheduler.captureEndOfDayAccountHistory();

        // Assert
        verify(fauxnanceClient, times(1)).getBatchedQuotes(List.of("GOOGL"));
        // networth = cash (10000) + AAPL from cache (10 * 170) + GOOGL from Fauxnance (5 * 140) = 12400
        verify(accountHistoryMapper, times(1))
                .insertAccountHistory(eq(1L), any(Instant.class),
                        eq(BigDecimal.valueOf(10000).setScale(2, RoundingMode.HALF_UP)),
                        argThat(nw -> nw.compareTo(BigDecimal.valueOf(12400)) == 0));
    }

    @Test
    @DisplayName("Manual trigger should return a summary of the run")
    void testManualTriggerReturnsSummary() {
        // Arrange
        when(accountMapper.findAllActiveAccounts()).thenReturn(List.of(activeAccount1, activeAccount2));
        when(positionMapper.findByAccountId(1L)).thenThrow(new RuntimeException("Database error"));
        when(positionMapper.findByAccountId(2L)).thenReturn(List.of());
        when(accountHistoryMapper.insertAccountHistory(anyLong(), any(Instant.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);

        // Act
        AccountHistorySnapshotResponse result = scheduler.triggerManualSnapshot();

        // Assert
        assertEquals(2, result.totalAccounts());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals(LocalDate.now(ZoneOffset.UTC), result.snapshotDate().atZone(ZoneOffset.UTC).toLocalDate());
    }

    @Test
    @DisplayName("Scheduled run should be skipped when disabled")
    void testScheduledRunSkippedWhenDisabled() {
        // Arrange
        ReflectionTestUtils.setField(scheduler, "scheduleEnabled", false);

        // Act
        scheduler.scheduledEndOfDayCapture();

        // Assert
        verifyNoInteractions(accountMapper, accountHistoryMapper);
    }
}
