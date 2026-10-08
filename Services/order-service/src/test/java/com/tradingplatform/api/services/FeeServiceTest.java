package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.domain.dto.FeeStructureResponse;
import com.tradingplatform.domain.dto.FeeUpdateRequest;
import com.tradingplatform.domain.exception.AssetTypeNotFoundException;
import com.tradingplatform.domain.model.AssetFeeStructure;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeeServiceTest {

    private AssetFeeMapper assetFeeMapper;
    private FeeService feeService;

    @BeforeEach
    void setUp() {
        assetFeeMapper = mock(AssetFeeMapper.class);
        feeService = new FeeService(assetFeeMapper);
    }

    @Test
    void updateFeeWritesNewFeesAndReturnsStoredStructure() {
        BigDecimal flat = new BigDecimal("7.50");
        BigDecimal pct = new BigDecimal("0.000300");
        when(assetFeeMapper.updateFees("EQUITY", flat, pct)).thenReturn(1);
        when(assetFeeMapper.findByAssetType("EQUITY")).thenReturn(new AssetFeeStructure("EQUITY", flat, pct));

        FeeStructureResponse result = feeService.updateFee("EQUITY", new FeeUpdateRequest(flat, pct));

        verify(assetFeeMapper).updateFees("EQUITY", flat, pct);
        assertEquals("EQUITY", result.assetType());
        assertEquals(flat, result.flatFee());
        assertEquals(pct, result.percentageFee());
    }

    @Test
    void updateFeeForUnknownAssetTypeThrowsNotFound() {
        when(assetFeeMapper.updateFees("BONDS", BigDecimal.ONE, BigDecimal.ZERO)).thenReturn(0);

        assertThrows(AssetTypeNotFoundException.class,
            () -> feeService.updateFee("BONDS", new FeeUpdateRequest(BigDecimal.ONE, BigDecimal.ZERO)));
    }

    @Test
    void getFeeForUnknownAssetTypeThrowsNotFound() {
        assertThrows(AssetTypeNotFoundException.class, () -> feeService.getFeeByAssetType("BONDS"));
    }

    @Test
    void updateRequestRejectsAllZeroNegativeAndOverHundredPercentFees() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertFalse(validator.validate(new FeeUpdateRequest(BigDecimal.ZERO, BigDecimal.ZERO)).isEmpty());
        assertFalse(validator.validate(new FeeUpdateRequest(new BigDecimal("-1"), new BigDecimal("0.001"))).isEmpty());
        assertFalse(validator.validate(new FeeUpdateRequest(BigDecimal.ONE, new BigDecimal("1.5"))).isEmpty());
        assertTrue(validator.validate(new FeeUpdateRequest(BigDecimal.ZERO, new BigDecimal("0.0001"))).isEmpty());
    }
}
