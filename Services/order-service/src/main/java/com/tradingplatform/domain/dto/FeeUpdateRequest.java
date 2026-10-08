package com.tradingplatform.domain.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request DTO for an admin updating the fee structure of one asset class.
 * percentageFee is in decimal form (0.0001 = 0.01%), matching the assets table.
 */
public record FeeUpdateRequest(
    @NotNull(message = "Flat fee cannot be null")
    @PositiveOrZero(message = "Flat fee cannot be negative")
    @Digits(integer = 16, fraction = 2, message = "Flat fee must have at most two decimal places")
    BigDecimal flatFee,

    @NotNull(message = "Percentage fee cannot be null")
    @PositiveOrZero(message = "Percentage fee cannot be negative")
    @DecimalMax(value = "1", message = "Percentage fee cannot exceed 100%")
    @Digits(integer = 12, fraction = 6, message = "Percentage fee must have at most six decimal places")
    BigDecimal percentageFee
) {

    /** Mirrors ck_assets_at_least_one: a trade must always carry some fee */
    @AssertTrue(message = "At least one of flat fee or percentage fee must be greater than zero")
    public boolean isAnyFeePositive() {
        return flatFee == null || percentageFee == null
            || flatFee.signum() > 0 || percentageFee.signum() > 0;
    }
}
