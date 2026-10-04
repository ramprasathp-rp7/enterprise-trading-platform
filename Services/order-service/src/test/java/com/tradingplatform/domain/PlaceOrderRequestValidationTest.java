package com.tradingplatform.domain;

import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderSide;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceOrderRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    private static PlaceOrderRequest validRequest() {
        return new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100, new BigDecimal("25.50"),
            "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
    }

    private <T> Set<ConstraintViolation<T>> validate(T request) {
        return validator.validate(request);
    }

    // --- the happy path -------------------------------------------------------

    @Test
    void validRequestPassesValidation() {
        assertTrue(validate(validRequest()).isEmpty());
    }

    // --- accountId: required, numeric key, at least 1 -------------------------------------------------------

    @Test
    void nullAccountIdIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(null, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void accountIdOfZeroIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(0L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void accountIdOfExactlyOneIsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertTrue(validate(request).isEmpty());
    }

    // --- symbol: required, not blank, at most 20 characters -------------------------------------------------------

    @Test
    void nullSymbolIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, null, OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void blankSymbolIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "   ", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void symbolAtMaxLengthOfTwentyIsAccepted() {
        String symbol = "A".repeat(20);
        PlaceOrderRequest request = new PlaceOrderRequest(1L, symbol, OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void symbolOverMaxLengthOfTwentyOneIsRejected() {
        String symbol = "A".repeat(21);
        PlaceOrderRequest request = new PlaceOrderRequest(1L, symbol, OrderSide.BUY, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    // --- side: required -------------------------------------------------------

    @Test
    void nullSideIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", null, 100,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    // --- quantity: required, whole units, greater than zero -------------------------------------------------------

    @Test
    void nullQuantityIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, null,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void quantityOfZeroIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 0,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void negativeQuantityIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, -1,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void quantityOfExactlyOneIsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 1,
            new BigDecimal("25.50"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertTrue(validate(request).isEmpty());
    }

    // --- price: required, greater than zero, at most two decimal places -------------------------------------------------------

    @Test
    void nullPriceIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            null, "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void zeroPriceIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            BigDecimal.ZERO, "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void negativePriceIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("-0.01"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void priceOfExactlyOneCentIsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("0.01"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void priceWithTwoDecimalPlacesIsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.99"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void priceWithThreeDecimalPlacesIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.999"), "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
        assertFalse(validate(request).isEmpty());
    }

    // --- idempotencyKey: required, between 8 and 100 characters -------------------------------------------------------

    @Test
    void nullIdempotencyKeyIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), null);
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void idempotencyKeyOfSevenCharactersIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "a".repeat(7));
        assertFalse(validate(request).isEmpty());
    }

    @Test
    void idempotencyKeyOfExactlyEightCharactersIsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "a".repeat(8));
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void idempotencyKeyOfExactlyOneHundredCharactersIsAccepted() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "a".repeat(100));
        assertTrue(validate(request).isEmpty());
    }

    @Test
    void idempotencyKeyOfOneHundredAndOneCharactersIsRejected() {
        PlaceOrderRequest request = new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, 100,
            new BigDecimal("25.50"), "a".repeat(101));
        assertFalse(validate(request).isEmpty());
    }

    // --- a request with multiple violations reports all of them -------------------------------------------------------

    @Test
    void aRequestBreakingSeveralConstraintsReportsAllOfThem() {
        PlaceOrderRequest request = new PlaceOrderRequest(0L, "", null, 0, BigDecimal.ZERO, "short");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validate(request);
        // accountId, symbol, side, quantity, price, idempotencyKey: six fields, six violations.
        assertEquals(6, violations.size());
    }
}
