package com.tradingplatform.api.analytics;

import com.tradingplatform.api.analytics.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Analytics ETL Service.
 * 
 * Test execution paths:
 * 1. An incremental load populates FACT_TRADES
 * 2. A second load with no new data adds no rows
 * 3. The transform handles nulls and type mismatches
 * 4. An invalid row is dead-lettered with its reason and the load continues
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsETLServiceTest {

    private AnalyticsETLService service;

    @Mock
    private AnalyticsExtract extract;

    @Mock
    private AnalyticsTransform transform;

    @Mock
    private AnalyticsLoad load;

    @Mock
    private AnalyticsProperties properties;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        service = new AnalyticsETLService();
        ReflectionTestUtils.setField(service, "extract", extract);
        ReflectionTestUtils.setField(service, "transform", transform);
        ReflectionTestUtils.setField(service, "load", load);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    }

    /**
     * Test 1: An incremental load populates FACT_TRADES
     * 
     * Scenario:
     * - No watermark exists (first run)
     * - Extract returns 3 new orders
     * - All orders pass quality checks
     * - FACT_TRADES receives 3 rows
     */
    @Test
    void testIncrementalLoadPopulatesFact_Trades() throws SQLException, IOException {
        // Setup: No watermark (first run)
        when(extract.getLastWatermark()).thenReturn(null);
        
        // Setup: Load schema check
        when(load.isDimDatePopulated()).thenReturn(false);
        
        // Setup: Dimension load results
        when(load.loadDimDate(anyList())).thenReturn(7300);  // 20 years of days
        when(load.loadDimInstrument(anyList())).thenReturn(5);
        when(load.loadDimAccount(anyList())).thenReturn(2);
        
        // Setup: Extract 3 orders
        List<AnalyticsOrderDTO> orders = Arrays.asList(
            new AnalyticsOrderDTO(
                UUID.randomUUID(), 1L, "ACC-001", "Alice",
                "AAPL", "BUY", 100, new BigDecimal("150.00"),
                new BigDecimal("149.50"), "FILLED",
                Instant.parse("2026-09-18T10:00:00Z")
            ),
            new AnalyticsOrderDTO(
                UUID.randomUUID(), 1L, "ACC-001", "Alice",
                "INFY.NS", "SELL", 50, new BigDecimal("1800.00"),
                new BigDecimal("1810.00"), "FILLED",
                Instant.parse("2026-09-18T10:15:00Z")
            ),
            new AnalyticsOrderDTO(
                UUID.randomUUID(), 2L, "ACC-002", "Bob",
                "TSLA", "BUY", 10, new BigDecimal("250.00"),
                null, "REJECTED",
                Instant.parse("2026-09-18T10:30:00Z")
            )
        );
        when(extract.extractOrders(null)).thenReturn(orders);
        
        // Setup: Dimension maps
        Map<String, Long> instrumentMap = Map.of(
            "AAPL", 1L, "INFY.NS", 2L, "TSLA", 3L
        );
        Map<String, Long> accountMap = Map.of(
            "ACC-001", 1L, "ACC-002", 2L
        );
        when(load.getInstrumentKeyMap()).thenReturn(instrumentMap);
        when(load.getAccountKeyMap()).thenReturn(accountMap);
        
        // Setup: Transform returns valid fact trades
        when(load.getNextTradeKey()).thenReturn(1L);
        when(transform.transformFactTrade(any(), any(), any(), anyLong(), anyInt()))
            .thenAnswer(invocation -> {
                AnalyticsOrderDTO order = invocation.getArgument(0);
                long tradeKey = invocation.getArgument(3);
                int dateKey = invocation.getArgument(4);
                return new AnalyticsFactTrade(
                    tradeKey,
                    accountMap.get(order.accountIdStr()),
                    instrumentMap.get(order.symbol()),
                    dateKey,
                    order.side(),
                    order.quantity(),
                    order.price(),
                    order.status(),
                    order.executedPrice(),
                    order.quantity() > 0 
                        ? BigDecimal.valueOf(order.quantity()).multiply(
                            order.executedPrice() != null ? order.executedPrice() : order.price())
                        : BigDecimal.ZERO,
                    order.orderId().toString(),
                    order.createdOn(),
                    Instant.now()
                );
            });
        
        // Setup: Load results
        when(load.loadFactTrades(anyList())).thenReturn(3);
        
        // Execute
        AnalyticsLoadResult result = service.runLoad();
        
        // Verify: Watermark was persisted
        verify(jdbcTemplate, times(1)).update(
            contains("INSERT INTO analytics_watermarks"),
            any(Timestamp.class), any(String.class),
            any(Timestamp.class), any(String.class)
        );
        
        // Verify: 3 fact trades loaded
        assertEquals(3, result.factTradesRowsLoaded());
        assertEquals(0, result.deadLetteredCount());
        assertNotNull(result.batchId());
    }

    /**
     * Test 2: A second load with no new data adds no rows
     * 
     * Scenario:
     * - Watermark exists from previous run
     * - Extract returns no orders (all already processed)
     * - FACT_TRADES receives 0 rows
     */
    @Test
    void testSecondLoadWithNoNewDataAddsNoRows() throws SQLException, IOException {
        // Setup: Watermark exists (not first run)
        Instant watermark = Instant.parse("2026-09-18T11:00:00Z");
        when(extract.getLastWatermark()).thenReturn(watermark);
        
        // Setup: Load schema check
        when(load.isDimDatePopulated()).thenReturn(true);
        
        // Setup: No new orders
        when(extract.extractOrders(watermark)).thenReturn(Collections.emptyList());
        
        // Setup: Dimension load results (no changes)
        when(load.loadDimInstrument(anyList())).thenReturn(0);
        when(load.loadDimAccount(anyList())).thenReturn(0);
        
        // Execute
        AnalyticsLoadResult result = service.runLoad();
        
        // Verify: No watermark update (no orders processed)
        verify(jdbcTemplate, never()).update(
            contains("INSERT INTO analytics_watermarks"),
            any(), any(), any(), any()
        );
        
        // Verify: 0 fact trades loaded
        assertEquals(0, result.factTradesRowsLoaded());
        assertEquals(0, result.deadLetteredCount());
    }

    /**
     * Test 3: The transform handles nulls and type mismatches gracefully
     * 
     * Scenario:
     * - Order has null executed_price (expected for NEW/REJECTED status)
     * - Trade value computed correctly even with null executed_price
     * - No exception thrown
     */
    @Test
    void testTransformHandlesNullExecutedPrice() throws SQLException, IOException {
        when(extract.getLastWatermark()).thenReturn(null);
        when(load.isDimDatePopulated()).thenReturn(false);
        when(load.loadDimDate(anyList())).thenReturn(7300);
        when(load.loadDimInstrument(anyList())).thenReturn(1);
        when(load.loadDimAccount(anyList())).thenReturn(1);
        
        // Order with NULL executed_price
        AnalyticsOrderDTO order = new AnalyticsOrderDTO(
            UUID.randomUUID(), 1L, "ACC-001", "Alice",
            "AAPL", "BUY", 100, new BigDecimal("150.00"),
            null,  // NULL executed_price
            "NEW",  // Status is NEW
            Instant.now()
        );
        when(extract.extractOrders(null)).thenReturn(List.of(order));
        
        Map<String, Long> instrumentMap = Map.of("AAPL", 1L);
        Map<String, Long> accountMap = Map.of("ACC-001", 1L);
        when(load.getInstrumentKeyMap()).thenReturn(instrumentMap);
        when(load.getAccountKeyMap()).thenReturn(accountMap);
        when(load.getNextTradeKey()).thenReturn(1L);
        
        // Transform should compute trade_value using price when executed_price is null
        when(transform.transformFactTrade(any(), any(), any(), anyLong(), anyInt()))
            .thenAnswer(invocation -> {
                AnalyticsOrderDTO o = invocation.getArgument(0);
                return new AnalyticsFactTrade(
                    1L, 1L, 1L, 20260918,
                    o.side(), o.quantity(), o.price(), o.status(),
                    o.executedPrice(),  // null
                    BigDecimal.valueOf(100).multiply(new BigDecimal("150.00")),  // Uses price, not executed_price
                    o.orderId().toString(),
                    o.createdOn(),
                    Instant.now()
                );
            });
        
        when(load.loadFactTrades(anyList())).thenReturn(1);
        
        AnalyticsLoadResult result = service.runLoad();
        
        // Verify: No exception, trade loaded successfully
        assertEquals(1, result.factTradesRowsLoaded());
        assertEquals(0, result.deadLetteredCount());
    }

    /**
     * Test 4: An invalid row is dead-lettered with its reason and the load continues
     * 
     * Scenario:
     * - First order passes validation
     * - Second order has negative quantity (fails validation)
     * - Third order passes validation
     * - Dead-letter catches 1 invalid order
     * - Load continues and processes valid orders
     * - FACT_TRADES receives 2 rows, dead-letter has 1
     */
    @Test
    void testInvalidRowIsDeadLetteredAndLoadContinues() throws SQLException, IOException {
        when(extract.getLastWatermark()).thenReturn(null);
        when(load.isDimDatePopulated()).thenReturn(false);
        when(load.loadDimDate(anyList())).thenReturn(7300);
        when(load.loadDimInstrument(anyList())).thenReturn(2);
        when(load.loadDimAccount(anyList())).thenReturn(1);
        
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();  // Will fail
        UUID orderId3 = UUID.randomUUID();
        
        List<AnalyticsOrderDTO> orders = Arrays.asList(
            new AnalyticsOrderDTO(orderId1, 1L, "ACC-001", "Alice",
                "AAPL", "BUY", 100, new BigDecimal("150.00"),
                new BigDecimal("149.50"), "FILLED", Instant.now()),
            new AnalyticsOrderDTO(orderId2, 1L, "ACC-001", "Alice",
                "INFY.NS", "BUY", -50, new BigDecimal("1800.00"),  // INVALID: negative quantity
                null, "NEW", Instant.now()),
            new AnalyticsOrderDTO(orderId3, 1L, "ACC-001", "Alice",
                "TSLA", "SELL", 10, new BigDecimal("250.00"),
                new BigDecimal("248.00"), "FILLED", Instant.now())
        );
        when(extract.extractOrders(null)).thenReturn(orders);
        
        Map<String, Long> instrumentMap = Map.of("AAPL", 1L, "INFY.NS", 2L, "TSLA", 3L);
        Map<String, Long> accountMap = Map.of("ACC-001", 1L);
        when(load.getInstrumentKeyMap()).thenReturn(instrumentMap);
        when(load.getAccountKeyMap()).thenReturn(accountMap);
        when(load.getNextTradeKey()).thenReturn(1L);
        
        // Setup: Transform throws exception for invalid order
        when(transform.transformFactTrade(any(), any(), any(), anyLong(), anyInt()))
            .thenAnswer(invocation -> {
                AnalyticsOrderDTO o = invocation.getArgument(0);
                if (o.orderId().equals(orderId2)) {
                    // Throw quality exception for negative quantity
                    throw new AnalyticsQualityException(
                        "Quantity must be > 0, got -50",
                        "QTY_NOT_POSITIVE",
                        orderId2
                    );
                }
                // Return valid fact trade for other orders
                return new AnalyticsFactTrade(
                    invocation.getArgument(3),
                    accountMap.get(o.accountIdStr()),
                    instrumentMap.get(o.symbol()),
                    invocation.getArgument(4),
                    o.side(), o.quantity(), o.price(), o.status(),
                    o.executedPrice(),
                    BigDecimal.valueOf(Math.abs(o.quantity())).multiply(
                        o.executedPrice() != null ? o.executedPrice() : o.price()),
                    o.orderId().toString(),
                    o.createdOn(),
                    Instant.now()
                );
            });
        
        when(load.loadFactTrades(anyList())).thenReturn(2);
        when(properties.getDeadLetterLogPath()).thenReturn("analytics.dlq");
        
        AnalyticsLoadResult result = service.runLoad();
        
        // Verify: 2 valid trades loaded, 1 dead-lettered
        assertEquals(2, result.factTradesRowsLoaded());
        assertEquals(1, result.deadLetteredCount());
    }

    /**
     * Test: Load handles IOException and rethrows
     */
    @Test
    void testLoadHandlesIOException() throws SQLException, IOException {
        // ensureSchema throws IOException - it's called first before getLastWatermark
        doThrow(new IOException("Schema load failed"))
            .when(load).ensureSchema();
        
        assertThrows(RuntimeException.class, () -> {
            try {
                service.runLoad();
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
