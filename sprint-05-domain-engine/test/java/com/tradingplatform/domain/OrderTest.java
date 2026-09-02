package com.tradingplatform.domain;

import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {

    private static Order newOrder() {
        return new Order(1L, 1L, "ACME", OrderSide.BUY, 100, new BigDecimal("25.50"), "idem-key-0001");
    }

    @Test
    void orderStartsAtNewWithNoExecutedPrice() {
        Order order = newOrder();
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertNull(order.getExecutedPrice());
    }

    @Test
    void newIsNotTerminalButTheOtherThreeAre() {
        assertTrue(!OrderStatus.NEW.isTerminal());
        assertTrue(OrderStatus.FILLED.isTerminal());
        assertTrue(OrderStatus.REJECTED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
    }

    @Test
    void markFilledSetsStatusAndExecutedPrice() {
        Order order = newOrder();
        order.markFilled(new BigDecimal("25.48"));
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(new BigDecimal("25.48"), order.getExecutedPrice());
    }

    @Test
    void markRejectedSetsStatusToRejected() {
        Order order = newOrder();
        order.markRejected();
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    @Test
    void cancelSetsStatusToCancelled() {
        Order order = newOrder();
        order.cancel();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void aFilledOrderRefusesToBeFilledAgain() {
        Order order = newOrder();
        order.markFilled(new BigDecimal("25.50"));
        assertThrows(IllegalStateException.class, () -> order.markFilled(new BigDecimal("25.50")));
    }

    @Test
    void aFilledOrderRefusesToBeCancelled() {
        Order order = newOrder();
        order.markFilled(new BigDecimal("25.50"));
        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void aRejectedOrderRefusesToBeFilled() {
        Order order = newOrder();
        order.markRejected();
        assertThrows(IllegalStateException.class, () -> order.markFilled(new BigDecimal("25.50")));
    }

    @Test
    void aCancelledOrderRefusesToBeRejected() {
        Order order = newOrder();
        order.cancel();
        assertThrows(IllegalStateException.class, order::markRejected);
    }

    @Test
    void quantityMustBeGreaterThanZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new Order(1L, 1L, "ACME", OrderSide.BUY, 0, new BigDecimal("25.50"), "idem-key-0001"));
    }

    @Test
    void priceMustBeGreaterThanZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new Order(1L, 1L, "ACME", OrderSide.BUY, 100, BigDecimal.ZERO, "idem-key-0001"));
    }
}
