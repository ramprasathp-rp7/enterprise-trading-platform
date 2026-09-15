package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountService.
 * 
 * Tests the 3 acceptance criteria execution paths:
 * 1. Account, balance, positions and order history returned successfully
 * 2. Unknown account returns ACC-404
 * 3. Authorization is validated at controller level (AccountAccessAuthorizer)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private InstrumentMapper instrumentMapper;

    private AccountService accountService;

    private static final long TEST_ACCOUNT_ID = 1L;
    private static final String TEST_ACCOUNT_ID_STR = "ACC-000001";
    private static final String TEST_HOLDER_NAME = "Priya Menon";
    private static final BigDecimal TEST_CASH_BALANCE = new BigDecimal("10000.00");
    private static final String TEST_CURRENCY = "USD";
    private static final int TEST_VERSION = 5;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountMapper, instrumentMapper);
    }

    // ============================================================================
    // Acceptance Criteria 1: Successful Read Operations
    // ============================================================================

    @Test
    @DisplayName("1.1 getAccount() returns AccountResponse successfully")
    void testGetAccount_ReturnsAccountResponseSuccessfully() {
        // Given
        Instant now = Instant.now();
        Account mockAccount = new Account(
            TEST_ACCOUNT_ID, TEST_ACCOUNT_ID_STR, TEST_HOLDER_NAME,
            TEST_CASH_BALANCE, TEST_CURRENCY, AccountStatus.ACTIVE, TEST_VERSION,
            now, now
        );
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);

        // When
        AccountResponse response = accountService.getAccount(TEST_ACCOUNT_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.accountId()).isEqualTo(TEST_ACCOUNT_ID_STR);
        assertThat(response.holderName()).isEqualTo(TEST_HOLDER_NAME);
        assertThat(response.cashBalance()).isEqualTo(TEST_CASH_BALANCE);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.version()).isEqualTo(TEST_VERSION);
        assertThat(response.lastUpdated()).isNotNull();

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
    }

    @Test
    @DisplayName("1.2 getBalance() returns BalanceResponse successfully")
    void testGetBalance_ReturnsBalanceResponseSuccessfully() {
        // Given
        Instant now = Instant.now();
        Account mockAccount = new Account(
            TEST_ACCOUNT_ID, TEST_ACCOUNT_ID_STR, TEST_HOLDER_NAME,
            TEST_CASH_BALANCE, TEST_CURRENCY, AccountStatus.ACTIVE, TEST_VERSION,
            now, now
        );
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);

        // When
        BalanceResponse response = accountService.getBalance(TEST_ACCOUNT_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.cashBalance()).isEqualTo(TEST_CASH_BALANCE);
        assertThat(response.currency()).isEqualTo(TEST_CURRENCY);
        assertThat(response.asOf()).isNotNull();

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
    }

    @Test
    @DisplayName("1.3 getPositions() returns list of PositionResponse successfully")
    void testGetPositions_ReturnsPositionListSuccessfully() {
        // Given
        Instant now = Instant.now();
        Account mockAccount = createMockAccount(now);
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);

        Position position1 = new Position(TEST_ACCOUNT_ID, 10L, 100, new BigDecimal("1500.00"));
        Position position2 = new Position(TEST_ACCOUNT_ID, 20L, 50, new BigDecimal("2300.00"));
        List<Position> positions = List.of(position1, position2);
        
        when(accountMapper.findPositionByAccountId(TEST_ACCOUNT_ID)).thenReturn(positions);
        
        Instrument instr1 = new Instrument(10L, "INFY", "Infosys", AssetClass.EQUITY, "INR", true);
        Instrument instr2 = new Instrument(20L, "RELIANCE", "Reliance Industries", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(10L)).thenReturn(instr1);
        when(instrumentMapper.findById(20L)).thenReturn(instr2);

        // When
        List<PositionResponse> response = accountService.getPositions(TEST_ACCOUNT_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
        assertThat(response.get(0).symbol()).isEqualTo("INFY");
        assertThat(response.get(0).quantity()).isEqualTo(100);
        assertThat(response.get(1).symbol()).isEqualTo("RELIANCE");
        assertThat(response.get(1).quantity()).isEqualTo(50);

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
        verify(accountMapper, times(1)).findPositionByAccountId(TEST_ACCOUNT_ID);
        verify(instrumentMapper, times(1)).findById(10L);
        verify(instrumentMapper, times(1)).findById(20L);
    }

    @Test
    @DisplayName("1.4 getPositions() returns empty list when no positions exist")
    void testGetPositions_ReturnsEmptyListWhenNoPositions() {
        // Given
        Instant now = Instant.now();
        Account mockAccount = createMockAccount(now);
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);
        when(accountMapper.findPositionByAccountId(TEST_ACCOUNT_ID)).thenReturn(List.of());

        // When
        List<PositionResponse> response = accountService.getPositions(TEST_ACCOUNT_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("1.5 getOrders() returns all orders without filters")
    void testGetOrders_ReturnsAllOrdersWithoutFilters() {
        // Given
        Instant now = Instant.now();
        Account mockAccount = createMockAccount(now);
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);

        Order order1 = createMockOrder("INFY", OrderStatus.FILLED, now.minusSeconds(3600));
        Order order2 = createMockOrder("RELIANCE", OrderStatus.NEW, now);
        List<Order> orders = List.of(order2, order1); // Most recent first

        when(accountMapper.findOrdersByAccountId(TEST_ACCOUNT_ID)).thenReturn(orders);
        
        // Mock instrument lookups for the orders (both use instrumentId 10L, so return RELIANCE)
        // In a real scenario, each order would have a different instrumentId
        Instrument instr = new Instrument(10L, "RELIANCE", "Reliance Industries", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(10L)).thenReturn(instr);

        // When
        List<OrderHistoryEntry> response = accountService.getOrders(
            TEST_ACCOUNT_ID, Optional.empty(), Optional.empty(), Optional.empty()
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
        // Both orders resolve to RELIANCE since they both have instrumentId 10L
        assertThat(response.get(0).symbol()).isEqualTo("RELIANCE"); // Most recent first
        assertThat(response.get(1).symbol()).isEqualTo("RELIANCE");

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
        verify(accountMapper, times(1)).findOrdersByAccountId(TEST_ACCOUNT_ID);
        verify(instrumentMapper, times(2)).findById(10L);
    }

    @Test
    @DisplayName("1.6 getOrders() filters by status only")
    void testGetOrders_FiltersByStatusOnly() {
        // Given
        Instant now = Instant.now();
        Account mockAccount = createMockAccount(now);
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);

        Order filledOrder = createMockOrder("INFY", OrderStatus.FILLED, now);
        List<Order> orders = List.of(filledOrder);

        when(accountMapper.findOrdersByAccountIdAndStatus(TEST_ACCOUNT_ID, OrderStatus.FILLED))
            .thenReturn(orders);

        Instrument instr = new Instrument(10L, "INFY", "Infosys", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(10L)).thenReturn(instr);

        // When
        List<OrderHistoryEntry> response = accountService.getOrders(
            TEST_ACCOUNT_ID, Optional.of(OrderStatus.FILLED), Optional.empty(), Optional.empty()
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.get(0).symbol()).isEqualTo("INFY");

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
        verify(accountMapper, times(1)).findOrdersByAccountIdAndStatus(TEST_ACCOUNT_ID, OrderStatus.FILLED);
        verify(instrumentMapper, times(1)).findById(10L);
    }

    @Test
    @DisplayName("1.7 getOrders() filters by date range only")
    void testGetOrders_FiltersByDateRangeOnly() {
        // Given
        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);
        Account mockAccount = createMockAccount(now);
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);
        
        Order order = createMockOrder("INFY", OrderStatus.FILLED, oneHourAgo);
        List<Order> orders = List.of(order);

        when(accountMapper.findOrdersByAccountIdAndFromAndTo(TEST_ACCOUNT_ID, oneHourAgo, now))
            .thenReturn(orders);

        Instrument instr = new Instrument(10L, "INFY", "Infosys", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(10L)).thenReturn(instr);

        // When
        List<OrderHistoryEntry> response = accountService.getOrders(
            TEST_ACCOUNT_ID, Optional.empty(), Optional.of(oneHourAgo), Optional.of(now)
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).symbol()).isEqualTo("INFY");

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
        verify(accountMapper, times(1)).findOrdersByAccountIdAndFromAndTo(
            eq(TEST_ACCOUNT_ID), any(Instant.class), any(Instant.class)
        );
        verify(instrumentMapper, times(1)).findById(10L);
    }

    @Test
    @DisplayName("1.8 getOrders() filters by status and date range")
    void testGetOrders_FiltersByStatusAndDateRange() {
        // Given
        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);
        Account mockAccount = createMockAccount(now);
        when(accountMapper.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(mockAccount);
        
        Order order = createMockOrder("INFY", OrderStatus.FILLED, oneHourAgo);
        List<Order> orders = List.of(order);

        when(accountMapper.findOrdersByAccountIdAndStatusAndFromAndTo(
            TEST_ACCOUNT_ID, OrderStatus.FILLED, oneHourAgo, now
        )).thenReturn(orders);

        Instrument instr = new Instrument(10L, "INFY", "Infosys", AssetClass.EQUITY, "INR", true);
        when(instrumentMapper.findById(10L)).thenReturn(instr);

        // When
        List<OrderHistoryEntry> response = accountService.getOrders(
            TEST_ACCOUNT_ID, Optional.of(OrderStatus.FILLED), Optional.of(oneHourAgo), Optional.of(now)
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.get(0).symbol()).isEqualTo("INFY");

        verify(accountMapper, times(1)).findByAccountId(TEST_ACCOUNT_ID);
        verify(accountMapper, times(1)).findOrdersByAccountIdAndStatusAndFromAndTo(
            eq(TEST_ACCOUNT_ID), eq(OrderStatus.FILLED), any(Instant.class), any(Instant.class)
        );
        verify(instrumentMapper, times(1)).findById(10L);
    }

    // ============================================================================
    // Acceptance Criteria 2: ACC-404 for Unknown Account
    // ============================================================================

    @Test
    @DisplayName("2.1 getAccount() throws ACC-404 for unknown account")
    void testGetAccount_ThrowsAccountNotFound_ForUnknownAccount() {
        // Given
        long unknownAccountId = 999L;
        when(accountMapper.findByAccountId(unknownAccountId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> accountService.getAccount(unknownAccountId))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining("Account not found");

        verify(accountMapper, times(1)).findByAccountId(unknownAccountId);
    }

    @Test
    @DisplayName("2.2 getBalance() throws ACC-404 for unknown account")
    void testGetBalance_ThrowsAccountNotFound_ForUnknownAccount() {
        // Given
        long unknownAccountId = 999L;
        when(accountMapper.findByAccountId(unknownAccountId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> accountService.getBalance(unknownAccountId))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining("Account not found");

        verify(accountMapper, times(1)).findByAccountId(unknownAccountId);
    }

    @Test
    @DisplayName("2.3 getPositions() throws ACC-404 for unknown account")
    void testGetPositions_ThrowsAccountNotFound_ForUnknownAccount() {
        // Given
        long unknownAccountId = 999L;
        when(accountMapper.findByAccountId(unknownAccountId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> accountService.getPositions(unknownAccountId))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining("Account not found");

        verify(accountMapper, times(1)).findByAccountId(unknownAccountId);
    }

    @Test
    @DisplayName("2.4 getOrders() throws ACC-404 for unknown account")
    void testGetOrders_ThrowsAccountNotFound_ForUnknownAccount() {
        // Given
        long unknownAccountId = 999L;
        when(accountMapper.findByAccountId(unknownAccountId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> accountService.getOrders(
            unknownAccountId, Optional.empty(), Optional.empty(), Optional.empty()
        ))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining("Account not found");

        verify(accountMapper, times(1)).findByAccountId(unknownAccountId);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private Account createMockAccount(Instant timestamp) {
        return new Account(
            TEST_ACCOUNT_ID, TEST_ACCOUNT_ID_STR, TEST_HOLDER_NAME,
            TEST_CASH_BALANCE, TEST_CURRENCY, AccountStatus.ACTIVE, TEST_VERSION,
            timestamp, timestamp
        );
    }

    private Order createMockOrder(String symbol, OrderStatus status, Instant createdOn) {
        Order order = new Order(
            TEST_ACCOUNT_ID, 10L, OrderSide.BUY, 100,
            new BigDecimal("1500.00"), "idempotency-key-" + UUID.randomUUID()
        );
        // Set status and executed price via reflection for testing
        try {
            java.lang.reflect.Field statusField = Order.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(order, status);
            
            if (status == OrderStatus.FILLED) {
                java.lang.reflect.Field executedPriceField = Order.class.getDeclaredField("executedPrice");
                executedPriceField.setAccessible(true);
                executedPriceField.set(order, new BigDecimal("1505.00"));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return order;
    }
}
