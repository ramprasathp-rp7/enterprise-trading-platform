package com.tradingplatform.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.ErrorCode;
import com.tradingplatform.enums.OrderSide;

class ExceptionTest {

    @Test
    void optimisticLockExceptionCarriesResourceAndErrorCode() {
        OptimisticLockException ex = new OptimisticLockException("Account", 42L);

        assertEquals("Account", ex.getResourceType());
        assertEquals(42L, ex.getResourceId());
        assertEquals(ErrorCode.ORD_409, ex.getErrorCode());
        assertEquals("ORD-409", ex.getCode());
        assertTrue(ex.getMessage().contains("Account 42"));
        assertNull(ex.getRejectedOrder());
    }

    @Test
    void withRejectedOrderAttachesOrderAndReturnsSelf() {
        OptimisticLockException ex = new OptimisticLockException("Position", "1:2");
        Order order = new Order(1L, 2L, OrderSide.BUY, 1, BigDecimal.ONE, "k");

        DomainException returned = ex.withRejectedOrder(order);

        assertSame(ex, returned);
        assertSame(order, ex.getRejectedOrder());
    }

    @Test
    void duplicateDeliveryExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        assertEquals("dup", new DuplicateDeliveryException("dup").getMessage());

        DuplicateDeliveryException withCause = new DuplicateDeliveryException("dup", cause);
        assertEquals("dup", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
