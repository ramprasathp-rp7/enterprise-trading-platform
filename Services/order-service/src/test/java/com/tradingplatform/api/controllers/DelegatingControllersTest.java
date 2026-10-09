package com.tradingplatform.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.AccountHistoryScheduler;
import com.tradingplatform.api.services.AccountHistoryService;
import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.api.services.FeeService;
import com.tradingplatform.api.services.OrderHistoryService;
import com.tradingplatform.api.services.OrderService;
import com.tradingplatform.api.services.TransactionService;
import com.tradingplatform.domain.dto.AccountHistoryResponse;
import com.tradingplatform.domain.dto.AccountHistorySnapshotResponse;
import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.FeeStructureResponse;
import com.tradingplatform.domain.dto.FeeUpdateRequest;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountUnauthorizedException;

/**
 * The account, order and admin controllers: each checks access (where it applies) and hands the
 * request to its service.
 */
class DelegatingControllersTest {

    private static final OffsetDateTime FROM = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime TO = OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.UTC);

    private final AccountAccessAuthorizer authorizer = mock(AccountAccessAuthorizer.class);

    @Test
    void accountEndpointsCheckAccessThenAnswerFromTheServices() {
        AccountService accounts = mock(AccountService.class);
        AccountHistoryService history = mock(AccountHistoryService.class);
        AccountController controller = new AccountController(authorizer, accounts, history);
        AccountResponse account = mock(AccountResponse.class);
        BalanceResponse balance = mock(BalanceResponse.class);
        List<PositionResponse> positions = List.of(mock(PositionResponse.class));
        List<OrderHistoryEntry> orders = List.of(mock(OrderHistoryEntry.class));
        List<AccountHistoryResponse> snapshots = List.of(mock(AccountHistoryResponse.class));
        when(accounts.getAccount(1L)).thenReturn(account);
        when(accounts.getBalance(1L)).thenReturn(balance);
        when(accounts.getPositions(1L)).thenReturn(positions);
        when(accounts.getOrders(1L, Optional.of(OrderStatus.FILLED), Optional.of(FROM.toInstant()), Optional.of(TO.toInstant())))
            .thenReturn(orders);
        when(accounts.getOrders(1L, Optional.empty(), Optional.empty(), Optional.empty())).thenReturn(List.of());
        when(history.getAccountHistory(1L, 30)).thenReturn(snapshots);
        when(history.getAccountHistoryRange(1L, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z")))
            .thenReturn(snapshots);

        assertThat(controller.getAccount(1L).getBody()).isSameAs(account);
        assertThat(controller.getBalance(1L).getBody()).isSameAs(balance);
        assertThat(controller.getPositions(1L).getBody()).isSameAs(positions);
        assertThat(controller.getOrders(1L, Optional.of(OrderStatus.FILLED), Optional.of(FROM), Optional.of(TO)).getBody())
            .isSameAs(orders);
        assertThat(controller.getOrders(1L, Optional.empty(), Optional.empty(), Optional.empty()).getBody()).isEmpty();
        assertThat(controller.getAccountHistory(1L, 30).getBody()).isSameAs(snapshots);
        // Both dates inclusive: the range runs to the start of the day after "to"
        assertThat(controller.getAccountHistoryRange(1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9)).getBody())
            .isSameAs(snapshots);
        verify(authorizer, org.mockito.Mockito.times(7)).assertTokenCanAccess(1L);
    }

    @Test
    void accountEndpointsStopAtTheAccessCheck() {
        AccountService accounts = mock(AccountService.class);
        AccountController controller = new AccountController(authorizer, accounts, mock(AccountHistoryService.class));
        doThrow(new AccountUnauthorizedException(2L, 1L)).when(authorizer).assertTokenCanAccess(2L);

        assertThatThrownBy(() -> controller.getBalance(2L)).isInstanceOf(AccountUnauthorizedException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void placingAnOrderReturns201WithItsLocation() {
        OrderService orders = mock(OrderService.class);
        OrderController controller = new OrderController(mock(OrderHistoryService.class), orders, authorizer);
        PlaceOrderRequest request = mock(PlaceOrderRequest.class);
        OrderResponse response = mock(OrderResponse.class);
        UUID id = UUID.randomUUID();
        when(request.accountId()).thenReturn(1L);
        when(response.orderId()).thenReturn("ORD-" + id);
        when(orders.placeOrder(request)).thenReturn(response);

        ResponseEntity<OrderResponse> result = controller.placeOrder(request);

        verify(authorizer).assertTokenCanAccess(1L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).hasToString("/api/v1/orders/" + id);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void cancellingAndOrderHistoryGoThroughTheServices() {
        OrderService orders = mock(OrderService.class);
        OrderHistoryService history = mock(OrderHistoryService.class);
        OrderController controller = new OrderController(history, orders, authorizer);
        UUID id = UUID.randomUUID();
        OrderResponse cancelled = mock(OrderResponse.class);
        List<OrderStatusChange> changes = List.of(new OrderStatusChange("ORD-" + id, Instant.now(), OrderStatus.NEW, OrderStatus.CANCELLED));
        when(orders.cancelOrderById(id.toString())).thenReturn(cancelled);
        when(history.getOrderAccountId(id)).thenReturn(4L);
        when(history.getOrderHistory(id)).thenReturn(changes);

        assertThat(controller.cancelOrder(id.toString()).getBody()).isSameAs(cancelled);
        assertThat(controller.getOrderStatusHistory(id.toString()).getBody()).isSameAs(changes);
        verify(authorizer).assertTokenCanAccess(4L);
    }

    @Test
    void adminHistoryUsesTheDateRangeOnlyWhenBothEndsAreGiven() {
        OrderHistoryService orderHistory = mock(OrderHistoryService.class);
        TransactionService transactions = mock(TransactionService.class);
        AdminController controller = new AdminController(orderHistory, transactions, mock(FeeService.class),
            mock(AccountHistoryScheduler.class));
        List<OrderStatusChange> ranged = List.of(mock(OrderStatusChange.class));
        List<OrderStatusChange> all = List.of(mock(OrderStatusChange.class), mock(OrderStatusChange.class));
        List<TransactionHistoryEntry> rangedTx = List.of(mock(TransactionHistoryEntry.class));
        List<TransactionHistoryEntry> allTx = List.of();
        when(orderHistory.getAllOrderHistory(FROM.toInstant(), TO.toInstant())).thenReturn(ranged);
        when(orderHistory.getAllOrderHistory()).thenReturn(all);
        when(transactions.getAllTransactions(FROM.toInstant(), TO.toInstant())).thenReturn(rangedTx);
        when(transactions.getAllTransactions()).thenReturn(allTx);

        assertThat(controller.getCompleteOrderHistory(Optional.of(FROM), Optional.of(TO)).getBody()).isSameAs(ranged);
        assertThat(controller.getCompleteOrderHistory(Optional.of(FROM), Optional.empty()).getBody()).isSameAs(all);
        assertThat(controller.getCompleteOrderHistory(Optional.empty(), Optional.empty()).getBody()).isSameAs(all);
        assertThat(controller.getCompleteTransactionHistory(Optional.of(FROM), Optional.of(TO)).getBody()).isSameAs(rangedTx);
        assertThat(controller.getCompleteTransactionHistory(Optional.empty(), Optional.of(TO)).getBody()).isSameAs(allTx);
    }

    @Test
    void adminFeesAndSnapshots() {
        FeeService fees = mock(FeeService.class);
        AccountHistoryScheduler scheduler = mock(AccountHistoryScheduler.class);
        AdminController controller = new AdminController(mock(OrderHistoryService.class), mock(TransactionService.class),
            fees, scheduler);
        FeeStructureResponse equity = mock(FeeStructureResponse.class);
        FeeUpdateRequest update = new FeeUpdateRequest(new BigDecimal("1.50"), new BigDecimal("0.001"));
        AccountHistorySnapshotResponse snapshot = mock(AccountHistorySnapshotResponse.class);
        when(fees.getAllFees()).thenReturn(List.of(equity));
        when(fees.getFeeByAssetType("EQUITY")).thenReturn(equity);
        when(fees.updateFee("EQUITY", update)).thenReturn(equity);
        when(scheduler.triggerManualSnapshot()).thenReturn(snapshot);

        assertThat(controller.getAllFees().getBody()).containsExactly(equity);
        assertThat(controller.getFeeByAssetType("EQUITY").getBody()).isSameAs(equity);
        assertThat(controller.updateFee("EQUITY", update).getBody()).isSameAs(equity);
        assertThat(controller.captureAccountHistorySnapshot().getBody()).isSameAs(snapshot);
    }
}
