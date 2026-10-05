package com.tradingplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;

class OrderTest {

    private Order newOrder() {
        return new Order(1L, 100L, OrderSide.BUY, 10, new BigDecimal("50.00"), "key-1");
    }

    @Test
    void newOrderDefaultsToNewWithGeneratedId() {
        Order o = newOrder();
        assertNotNull(o.getId());
        assertEquals(1L, o.getAccountId());
        assertEquals(100L, o.getInstrumentId());
        assertEquals(OrderSide.BUY, o.getSide());
        assertEquals(10, o.getQuantity());
        assertEquals(new BigDecimal("50.00"), o.getPrice());
        assertEquals("key-1", o.getIdempotencyKey());
        assertNotNull(o.getCreatedOn());
        assertEquals(OrderStatus.NEW, o.getStatus());
        assertNull(o.getExecutedPrice());
        assertNull(o.getFee());
    }

    @Test
    void constructorWithIdKeepsSuppliedId() {
        UUID id = UUID.randomUUID();
        Order o = new Order(id, 1L, 100L, OrderSide.SELL, 5, new BigDecimal("1.00"), "k");
        assertEquals(id, o.getId());
        assertEquals(OrderStatus.NEW, o.getStatus());
    }

    @Test
    void invalidQuantityOrPriceIsRejectedByBothConstructors() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> new Order(1L, 100L, OrderSide.BUY, 0, BigDecimal.ONE, "k"));
        assertThrows(IllegalArgumentException.class,
            () -> new Order(1L, 100L, OrderSide.BUY, 1, BigDecimal.ZERO, "k"));
        assertThrows(IllegalArgumentException.class,
            () -> new Order(id, 1L, 100L, OrderSide.BUY, -1, BigDecimal.ONE, "k"));
        assertThrows(IllegalArgumentException.class,
            () -> new Order(id, 1L, 100L, OrderSide.BUY, 1, new BigDecimal("-1"), "k"));
        assertThrows(NullPointerException.class,
            () -> new Order(null, 1L, 100L, OrderSide.BUY, 1, BigDecimal.ONE, "k"));
        assertThrows(NullPointerException.class,
            () -> new Order(1L, 100L, OrderSide.BUY, 1, BigDecimal.ONE, null));
    }

    @Test
    void reconstructionConstructorPreservesPersistedState() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Order o = new Order(id, 1L, 100L, OrderSide.BUY, 3, new BigDecimal("10.00"), "k",
            created, OrderStatus.FILLED, new BigDecimal("9.99"), new BigDecimal("0.50"));

        assertEquals(created, o.getCreatedOn());
        assertEquals(OrderStatus.FILLED, o.getStatus());
        assertEquals(new BigDecimal("9.99"), o.getExecutedPrice());
        assertEquals(new BigDecimal("0.50"), o.getFee());
        assertThrows(NullPointerException.class, () -> new Order(id, 1L, 100L, OrderSide.BUY, 3,
            BigDecimal.TEN, "k", created, null, null, null));
    }

    @Test
    void setFeeAcceptsNullAndNonNegative() {
        Order o = newOrder();
        o.setFee(new BigDecimal("1.25"));
        assertEquals(new BigDecimal("1.25"), o.getFee());
        o.setFee(null);
        assertNull(o.getFee());
        assertThrows(IllegalArgumentException.class, () -> o.setFee(new BigDecimal("-0.01")));
    }

    @Test
    void markFilledSetsPriceAndStatus() {
        Order o = newOrder();
        o.markFilled(new BigDecimal("49.00"));
        assertEquals(OrderStatus.FILLED, o.getStatus());
        assertEquals(new BigDecimal("49.00"), o.getExecutedPrice());
    }

    @Test
    void markFilledWithFeeSetsPriceFeeAndStatus() {
        Order o = newOrder();
        o.markFilled(new BigDecimal("49.00"), new BigDecimal("2.00"));
        assertEquals(OrderStatus.FILLED, o.getStatus());
        assertEquals(new BigDecimal("2.00"), o.getFee());
    }

    @Test
    void markFilledWithNegativeFeeIsRejected() {
        Order o = newOrder();
        assertThrows(IllegalArgumentException.class,
            () -> o.markFilled(new BigDecimal("49.00"), new BigDecimal("-1")));
        assertThrows(NullPointerException.class, () -> newOrder().markFilled(null));
    }

    @Test
    void markRejectedAndCancelTransitionFromNew() {
        Order rejected = newOrder();
        rejected.markRejected();
        assertEquals(OrderStatus.REJECTED, rejected.getStatus());

        Order cancelled = newOrder();
        cancelled.cancel();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void terminalOrdersCannotTransitionAgain() {
        Order o = newOrder();
        o.markRejected();
        assertThrows(IllegalStateException.class, () -> o.markFilled(BigDecimal.ONE));
        assertThrows(IllegalStateException.class, () -> o.markFilled(BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(IllegalStateException.class, o::markRejected);
        assertThrows(IllegalStateException.class, o::cancel);
    }
}
