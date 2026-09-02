package com.tradingplatform.domain;

import com.tradingplatform.domain.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionTest {

    @Test
    void emptyPositionHasZeroQuantityAndZeroAverageCost() {
        Position position = Position.empty(1L, 1L);
        assertEquals(0, position.getQuantity());
        assertEquals(new BigDecimal("0.000000"), position.getAverageCost());
    }

    @Test
    void firstBuySetsAverageCostToThePriceItWasBoughtAt() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("25.50"));
        assertEquals(10, position.getQuantity());
        assertEquals(new BigDecimal("25.500000"), position.getAverageCost());
    }

    @Test
    void secondBuyRecalculatesTheWeightedAverage() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00")); // cost basis 200
        position.applyBuy(10, new BigDecimal("30.00")); // cost basis 300, total 500 / 20 = 25
        assertEquals(20, position.getQuantity());
        assertEquals(new BigDecimal("25.000000"), position.getAverageCost());
    }

    @Test
    void sellReducesQuantityAndLeavesAverageCostUnchanged() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00"));
        position.applySell(4);
        assertEquals(6, position.getQuantity());
        assertEquals(new BigDecimal("20.000000"), position.getAverageCost());
    }

    @Test
    void sellOfExactlyTheHeldQuantityLeavesZero() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00"));
        position.applySell(10);
        assertEquals(0, position.getQuantity());
        assertEquals(new BigDecimal("20.000000"), position.getAverageCost());
    }

    @Test
    void sellOfMoreThanHeldIsRefused() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00"));
        assertThrows(IllegalStateException.class, () -> position.applySell(11));
    }

    @Test
    void quantityNeverGoesNegativeAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1L, 1L, -1, BigDecimal.ZERO));
    }

    @Test
    void buyOfZeroOrFewerUnitsIsRejected() {
        Position position = Position.empty(1L, 1L);
        assertThrows(IllegalArgumentException.class, () -> position.applyBuy(0, new BigDecimal("20.00")));
    }

    @Test
    void reconciliationAgainstFilledOrdersMatchesTheStoredPosition() {
        Position replayed = Position.empty(1L, 1L);
        replayed.applyBuy(10, new BigDecimal("20.00"));
        replayed.applyBuy(5, new BigDecimal("26.00"));
        replayed.applySell(3);

        // (10*20 + 5*26) / 15 = 330/15 = 22.00
        assertEquals(12, replayed.getQuantity());
        assertEquals(new BigDecimal("22.000000"), replayed.getAverageCost());
    }
}
