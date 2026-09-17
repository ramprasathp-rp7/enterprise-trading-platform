package com.tradingplatform.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for AssetFeeStructure fee calculation.
 */
@DisplayName("AssetFeeStructure - Fee Calculation Tests")
class AssetFeeStructureTest {

    /**
     * TEST 1: Fee calculation with flat + percentage model
     * Total Fee = flatFee + (notional * percentageFee)
     */
    @Test
    @DisplayName("Test 1: Calculate fee with flat and percentage components")
    void calculateFeeWithFlatAndPercentage() {
        // Arrange: EQUITY has $5 flat + 0.01% of notional
        AssetFeeStructure equityFee = new AssetFeeStructure("EQUITY", 
            new BigDecimal("5.00"), 
            new BigDecimal("0.0001"));  // 0.01%
        
        BigDecimal notional = new BigDecimal("10000.00");  // $10,000 trade
        
        // Act
        BigDecimal fee = equityFee.calculateFee(notional);
        
        // Assert: Fee = 5 + (10000 * 0.0001) = 5 + 1 = $6.00
        assertEquals(new BigDecimal("6.00"), fee, "Fee should be $6.00");
    }

    /**
     * TEST 2: Fee calculation with only flat fee
     */
    @Test
    @DisplayName("Test 2: Calculate fee with only flat component")
    void calculateFeeWithOnlyFlat() {
        // Arrange: FX fee has $2.50 flat + 0% percentage
        AssetFeeStructure fxFee = new AssetFeeStructure("FX", 
            new BigDecimal("2.50"), 
            new BigDecimal("0.0000"));
        
        BigDecimal notional = new BigDecimal("5000.00");
        
        // Act
        BigDecimal fee = fxFee.calculateFee(notional);
        
        // Assert: Fee = $2.50
        assertEquals(new BigDecimal("2.50"), fee, "Fee should be $2.50");
    }

    /**
     * TEST 3: Fee calculation with only percentage fee
     */
    @Test
    @DisplayName("Test 3: Calculate fee with only percentage component")
    void calculateFeeWithOnlyPercentage() {
        // Arrange: Custom fee with $0 flat + 0.05% percentage
        AssetFeeStructure customFee = new AssetFeeStructure("CRYPTO", 
            new BigDecimal("0.00"), 
            new BigDecimal("0.0005"));  // 0.05%
        
        BigDecimal notional = new BigDecimal("2000.00");
        
        // Act
        BigDecimal fee = customFee.calculateFee(notional);
        
        // Assert: Fee = 0 + (2000 * 0.0005) = $1.00
        assertEquals(new BigDecimal("1.00"), fee, "Fee should be $1.00");
    }

    /**
     * TEST 4: Fee rounds to 2 decimal places
     */
    @Test
    @DisplayName("Test 4: Fee rounds to 2 decimal places")
    void feesRoundTo2DecimalPlaces() {
        // Arrange: Fee structure that produces result needing rounding
        AssetFeeStructure fee = new AssetFeeStructure("EQUITY", 
            new BigDecimal("1.50"), 
            new BigDecimal("0.00033"));  // 0.033%
        
        BigDecimal notional = new BigDecimal("1234.56");
        
        // Act
        BigDecimal calculated = fee.calculateFee(notional);
        
        // Assert: Fee = 1.50 + (1234.56 * 0.00033) = 1.50 + 0.4074... = 1.9074... → rounds to 1.91
        assertEquals(new BigDecimal("1.91"), calculated, 
            "Fee should round to 2 decimal places");
        assertEquals(2, calculated.scale(), "Fee should have exactly 2 decimal places");
    }

    /**
     * TEST 5: Constructor validation - rejects both zero fees
     */
    @Test
    @DisplayName("Test 5: Constructor rejects both zero fees")
    void constructorRejectsBothZeroFees() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> new AssetFeeStructure("TEST", 
                new BigDecimal("0.00"), 
                new BigDecimal("0.00")),
            "Should reject when both flat and percentage are zero");
    }

    /**
     * TEST 6: Constructor validation - rejects negative flat fee
     */
    @Test
    @DisplayName("Test 6: Constructor rejects negative flat fee")
    void constructorRejectsNegativeFlatFee() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> new AssetFeeStructure("TEST", 
                new BigDecimal("-1.00"), 
                new BigDecimal("0.001")),
            "Should reject negative flatFee");
    }

    /**
     * TEST 7: Constructor validation - rejects negative percentage fee
     */
    @Test
    @DisplayName("Test 7: Constructor rejects negative percentage fee")
    void constructorRejectsNegativePercentageFee() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> new AssetFeeStructure("TEST", 
                new BigDecimal("1.00"), 
                new BigDecimal("-0.001")),
            "Should reject negative percentageFee");
    }

    /**
     * TEST 8: Getters return correct values
     */
    @Test
    @DisplayName("Test 8: Getters return correct values")
    void gettersReturnCorrectValues() {
        // Arrange
        AssetFeeStructure fee = new AssetFeeStructure("CRYPTO", 
            new BigDecimal("10.00"), 
            new BigDecimal("0.002"));
        
        // Assert
        assertEquals("CRYPTO", fee.getAssetType());
        assertEquals(new BigDecimal("10.00"), fee.getFlatFee());
        assertEquals(new BigDecimal("0.002"), fee.getPercentageFee());
    }

    /**
     * TEST 9: Zero notional results in only flat fee
     */
    @Test
    @DisplayName("Test 9: Zero notional results in only flat fee")
    void zeroNotionalResultsInOnlyFlatFee() {
        // Arrange
        AssetFeeStructure fee = new AssetFeeStructure("EQUITY", 
            new BigDecimal("5.00"), 
            new BigDecimal("0.0001"));
        
        // Act
        BigDecimal calculated = fee.calculateFee(BigDecimal.ZERO);
        
        // Assert
        assertEquals(new BigDecimal("5.00"), calculated);
    }
}
