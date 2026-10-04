package com.tradingplatform.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertAll;
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
    void buysRecalculateTheWeightedAverageAcrossOldAndNewUnits() {
        Position position = Position.empty(1L, 1L);

        position.applyBuy(10, new BigDecimal("25.50"));
        assertAll(
            () -> assertEquals(10, position.getQuantity()),
            () -> assertEquals(new BigDecimal("25.500000"), position.getAverageCost())
        );

        position.applyBuy(10, new BigDecimal("30.00")); // (10*25.50 + 10*30.00) / 20 = 27.75
        assertAll(
            () -> assertEquals(20, position.getQuantity()),
            () -> assertEquals(new BigDecimal("27.750000"), position.getAverageCost())
        );
    }

    @Test
    void sellReducesQuantityAndLeavesAverageCostUnchangedIncludingSellingTheEntireHolding() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00"));

        position.applySell(4);
        assertAll(
            () -> assertEquals(6, position.getQuantity()),
            () -> assertEquals(new BigDecimal("20.000000"), position.getAverageCost())
        );

        position.applySell(6);
        assertAll(
            () -> assertEquals(0, position.getQuantity()),
            () -> assertEquals(new BigDecimal("20.000000"), position.getAverageCost())
        );
    }

    @Test
    void sellOfMoreThanHeldIsRefused() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00"));
        assertThrows(IllegalStateException.class, () -> position.applySell(11));
    }

    @Test
    void constructionAndBuyRejectNonPositiveQuantities() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> new Position(1L, 1L, -1, BigDecimal.ZERO)),
            () -> assertThrows(IllegalArgumentException.class, () ->
                Position.empty(1L, 1L).applyBuy(0, new BigDecimal("20.00")))
        );
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