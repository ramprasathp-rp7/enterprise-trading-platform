package com.tradingplatform.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.OrderHistoryMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.OrderNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.services.AvailableBalanceService;

class OrderHistoryAndBalanceServiceTest {

    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T00:00:00Z");

    private final OrderHistoryMapper historyMapper = mock(OrderHistoryMapper.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderHistoryService history = new OrderHistoryService(historyMapper, orderMapper);

    private static Order order(long accountId, String price, int quantity, String fee) {
        Order order = new Order(accountId, 2L, OrderSide.BUY, quantity, new BigDecimal(price), "key-" + UUID.randomUUID());
        if (fee != null) {
            order.setFee(new BigDecimal(fee));
        }
        return order;
    }

    private static OrderStatusChange change(UUID id) {
        return new OrderStatusChange(id.toString(), TO, OrderStatus.NEW, OrderStatus.FILLED);
    }

    @Test
    void orderHistoryPrefixesOrderIdsAndChecksTheOrderExists() {
        UUID id = UUID.randomUUID();
        when(orderMapper.findOrderById(id)).thenReturn(order(3L, "10", 1, null));
        when(historyMapper.findByOrderId(id)).thenReturn(List.of(change(id)));
        when(historyMapper.findByOrderIdAndDateRange(id, FROM, TO)).thenReturn(List.of(change(id)));

        assertThat(history.getOrderAccountId(id)).isEqualTo(3L);
        assertThat(history.getOrderHistory(id)).extracting(OrderStatusChange::orderId).containsExactly("ORD-" + id);
        assertThat(history.getOrderHistory(id, FROM, TO)).singleElement()
            .satisfies(c -> {
                assertThat(c.orderId()).isEqualTo("ORD-" + id);
                assertThat(c.toStatus()).isEqualTo(OrderStatus.FILLED);
            });
    }

    @Test
    void unknownOrdersAreNotFound() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> history.getOrderHistory(id)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> history.getOrderAccountId(id)).isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void accountAndAdminHistoryRejectBackwardsRanges() {
        UUID id = UUID.randomUUID();
        when(orderMapper.findOrderById(id)).thenReturn(order(3L, "10", 1, null));
        when(historyMapper.findByAccountIdAndDateRange(3L, FROM, TO)).thenReturn(List.of(change(id)));
        when(historyMapper.findAllOrderHistory()).thenReturn(List.of(change(id), change(id)));
        when(historyMapper.findAllOrderHistoryByDateRange(FROM, TO)).thenReturn(List.of(change(id)));

        assertThat(history.getAccountOrderHistory(3L, FROM, TO)).hasSize(1);
        assertThat(history.getAllOrderHistory()).hasSize(2).allMatch(c -> c.orderId().startsWith("ORD-"));
        assertThat(history.getAllOrderHistory(FROM, TO)).hasSize(1);

        assertThatThrownBy(() -> history.getOrderHistory(id, TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.getAccountOrderHistory(3L, TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.getAllOrderHistory(TO, FROM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availableBalanceHoldsBackEveryPendingOrderWithItsFee() {
        AccountMapper accounts = mock(AccountMapper.class);
        AvailableBalanceService service = new AvailableBalanceService(accounts, orderMapper);
        when(accounts.findByAccountId(1L)).thenReturn(
            new Account(1L, "ACC-1", "Rahul", new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, 0));
        when(orderMapper.findOrdersByAccountIdAndStatus(1L, OrderStatus.NEW))
            .thenReturn(List.of(order(1L, "100.00", 2, "1.50"), order(1L, "50.00", 1, null)));

        // 1000 - (200 + 1.50) - 50
        assertThat(service.getAvailableBalance(1L)).isEqualByComparingTo("748.50");
    }

    @Test
    void availableBalanceIsTheCashWhenNothingIsPendingAndFailsForUnknownAccounts() {
        AccountMapper accounts = mock(AccountMapper.class);
        AvailableBalanceService service = new AvailableBalanceService(accounts, orderMapper);
        when(accounts.findByAccountId(1L)).thenReturn(
            new Account(1L, "ACC-1", "Rahul", new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, 0));
        when(orderMapper.findOrdersByAccountIdAndStatus(1L, OrderStatus.NEW)).thenReturn(List.of());

        assertThat(service.getAvailableBalance(1L)).isEqualByComparingTo("1000.00");
        assertThatThrownBy(() -> service.getAvailableBalance(9L)).isInstanceOf(IllegalStateException.class);
    }
}
