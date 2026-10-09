package com.tradingplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.tradingplatform.enums.AssetClass;

class AssetFeeStructureTest {

    @Test
    void feeIsFlatPlusPercentageRoundedToCents() {
        AssetFeeStructure fee = new AssetFeeStructure("EQUITY", new BigDecimal("1.00"), new BigDecimal("0.001"));
        // 1.00 + 1234.56 * 0.001 = 2.23456
        assertEquals(new BigDecimal("2.23"), fee.calculateFee(new BigDecimal("1234.56")));
    }

    @Test
    void flatOnlyAndPercentageOnlyAreAllowed() {
        AssetFeeStructure flat = new AssetFeeStructure("FX", new BigDecimal("5"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("5.00"), flat.calculateFee(new BigDecimal("1000")));

        AssetFeeStructure pct = new AssetFeeStructure("CRYPTO", BigDecimal.ZERO, new BigDecimal("0.01"));
        assertEquals(new BigDecimal("10.00"), pct.calculateFee(new BigDecimal("1000")));
        assertEquals(new BigDecimal("0.00"), pct.calculateFee(BigDecimal.ZERO));
    }

    @Test
    void gettersAndToStringExposeConfiguration() {
        AssetFeeStructure fee = new AssetFeeStructure("EQUITY", new BigDecimal("1.00"), new BigDecimal("0.001"));
        assertEquals("EQUITY", fee.getAssetType());
        assertEquals(new BigDecimal("1.00"), fee.getFlatFee());
        assertEquals(new BigDecimal("0.001"), fee.getPercentageFee());
        assertTrue(fee.toString().contains("assetType='EQUITY'"));
    }

    @Test
    void constructorRejectsInvalidConfigurations() {
        assertThrows(IllegalArgumentException.class,
            () -> new AssetFeeStructure("X", BigDecimal.ZERO, BigDecimal.ZERO));
        BigDecimal negative = new BigDecimal("-1");
        BigDecimal oneCent = new BigDecimal("0.01");
        BigDecimal negativeCent = new BigDecimal("-0.01");
        assertThrows(IllegalArgumentException.class,
            () -> new AssetFeeStructure("X", negative, oneCent));
        assertThrows(IllegalArgumentException.class,
            () -> new AssetFeeStructure("X", BigDecimal.ONE, negativeCent));
        assertThrows(NullPointerException.class,
            () -> new AssetFeeStructure(null, BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void calculateFeeRejectsNullOrNegativeVolume() {
        AssetFeeStructure fee = new AssetFeeStructure("EQUITY", BigDecimal.ONE, BigDecimal.ZERO);
        assertThrows(NullPointerException.class, () -> fee.calculateFee(null));
        BigDecimal negativeVolume = new BigDecimal("-1");
        assertThrows(IllegalArgumentException.class, () -> fee.calculateFee(negativeVolume));
    }

    @Test
    void instrumentFactoriesSetTradableFlag() {
        Instrument listed = Instrument.tradable(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD");
        Instrument delisted = Instrument.delisted(2L, "OLD", "Old Co", AssetClass.EQUITY, "USD");

        assertTrue(listed.isTradable());
        assertFalse(delisted.isTradable());
        assertEquals(1L, listed.getId());
        assertEquals("AAPL", listed.getSymbol());
        assertEquals("Apple", listed.getName());
        assertEquals(AssetClass.EQUITY, listed.getAssetClass());
        assertEquals("USD", listed.getCurrency());
    }
}
