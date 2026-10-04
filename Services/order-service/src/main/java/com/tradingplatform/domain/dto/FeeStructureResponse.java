package com.tradingplatform.domain.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Response DTO for asset fee structure information.
 * Represents the fees charged for trading different asset classes.
 */
public record FeeStructureResponse(
    @NotBlank(message = "Asset type cannot be blank")
    String assetType,

    @NotNull(message = "Flat fee cannot be null")
    @PositiveOrZero(message = "Flat fee cannot be negative")
    @Digits(integer = 18, fraction = 2, message = "Flat fee must have at most two decimal places")
    BigDecimal flatFee,

    @NotNull(message = "Percentage fee cannot be null")
    @PositiveOrZero(message = "Percentage fee cannot be negative")
    @Digits(integer = 18, fraction = 6, message = "Percentage fee must have at most six decimal places")
    BigDecimal percentageFee
) {
}
