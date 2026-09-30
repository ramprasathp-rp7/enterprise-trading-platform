package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {

    private static Order newOrder() {
        return new Order(1L, 1L, OrderSide.BUY, 100, new BigDecimal("25.50"), "idem-key-0001");
    }

    @Test
    void orderStartsAtNewWithNoExecutedPrice() {
        Order order = newOrder();
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertNull(order.getExecutedPrice());
        assertNull(order.getFee());
    }

    @Test
    void newIsNotTerminalButTheOtherThreeStatusesAre() {
        assertAll(
            () -> assertTrue(!OrderStatus.NEW.isTerminal(), "NEW is the working state"),
            () -> assertTrue(OrderStatus.FILLED.isTerminal()),
            () -> assertTrue(OrderStatus.REJECTED.isTerminal()),
            () -> assertTrue(OrderStatus.CANCELLED.isTerminal())
        );
    }

    @Test
    void markFilledMarkRejectedAndCancelEachSetTheExpectedStatus() {
        Order filled = newOrder();
        filled.markFilled(new BigDecimal("25.48"), new BigDecimal("5.00"));

        Order rejected = newOrder();
        rejected.markRejected();

        Order cancelled = newOrder();
        cancelled.cancel();

        assertAll(
            () -> assertEquals(OrderStatus.FILLED, filled.getStatus()),
            () -> assertEquals(new BigDecimal("25.48"), filled.getExecutedPrice()),
            () -> assertEquals(new BigDecimal("5.00"), filled.getFee()),
            () -> assertEquals(OrderStatus.REJECTED, rejected.getStatus()),
            () -> assertEquals(OrderStatus.CANCELLED, cancelled.getStatus())
        );
    }

    @Test
    void aFilledOrderRefusesToBeFilledAgainOrCancelled() {
        Order order = newOrder();
        order.markFilled(new BigDecimal("25.50"), new BigDecimal("5.00"));
        assertAll(
            () -> assertThrows(IllegalStateException.class, () -> order.markFilled(new BigDecimal("25.50"), new BigDecimal("5.00"))),
            () -> assertThrows(IllegalStateException.class, order::cancel)
        );
    }

    @Test
    void aRejectedOrCancelledOrderRefusesFurtherTransitions() {
        Order rejected = newOrder();
        rejected.markRejected();

        Order cancelled = newOrder();
        cancelled.cancel();

        assertAll(
            () -> assertThrows(IllegalStateException.class, () -> rejected.markFilled(new BigDecimal("25.50"), new BigDecimal("5.00"))),
            () -> assertThrows(IllegalStateException.class, cancelled::markRejected)
        );
    }

    @Test
    void quantityAndPriceMustBeGreaterThanZeroAtConstruction() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () ->
                new Order(1L, 1L, OrderSide.BUY, 0, new BigDecimal("25.50"), "idem-key-0001")),
            () -> assertThrows(IllegalArgumentException.class, () ->
                new Order(1L, 1L, OrderSide.BUY, 100, BigDecimal.ZERO, "idem-key-0001"))
        );
    }
}