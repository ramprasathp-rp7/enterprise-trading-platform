package com.tradingplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void emptyPositionHasZeroQuantityAndCost() {
        Position p = Position.empty(1L, 100L);
        assertEquals(1L, p.getAccountId());
        assertEquals(100L, p.getInstrumentId());
        assertEquals(0, p.getQuantity());
        assertEquals(new BigDecimal("0.000000"), p.getAverageCost());
        assertEquals(0, p.getVersion());
    }

    @Test
    void constructorValidatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1L, 100L, -1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Position(1L, 100L, 1, BigDecimal.ONE, -1));
        assertThrows(NullPointerException.class, () -> new Position(null, 100L, 1, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new Position(1L, 100L, 1, null));
    }

    @Test
    void buyRecalculatesWeightedAverageCost() {
        Position p = new Position(1L, 100L, 10, new BigDecimal("100"), 2);
        p.applyBuy(10, new BigDecimal("110"));
        assertEquals(20, p.getQuantity());
        assertEquals(new BigDecimal("105.000000"), p.getAverageCost());
        assertEquals(2, p.getVersion());
    }

    @Test
    void buyIntoEmptyPositionUsesPurchasePrice() {
        Position p = Position.empty(1L, 100L);
        p.applyBuy(3, new BigDecimal("12.34"));
        assertEquals(3, p.getQuantity());
        assertEquals(new BigDecimal("12.340000"), p.getAverageCost());
    }

    @Test
    void buyRejectsInvalidInput() {
        Position p = Position.empty(1L, 100L);
        assertThrows(IllegalArgumentException.class, () -> p.applyBuy(0, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> p.applyBuy(1, null));
    }

    @Test
    void sellReducesQuantityAndKeepsAverageCost() {
        Position p = new Position(1L, 100L, 10, new BigDecimal("100"));
        p.applySell(4);
        assertEquals(6, p.getQuantity());
        assertEquals(new BigDecimal("100.000000"), p.getAverageCost());
    }

    @Test
    void sellRejectsInvalidOrOversizedQuantity() {
        Position p = new Position(1L, 100L, 5, new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class, () -> p.applySell(0));
        assertThrows(IllegalStateException.class, () -> p.applySell(6));
        assertEquals(5, p.getQuantity());
    }
}
