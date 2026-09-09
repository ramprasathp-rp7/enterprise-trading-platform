package com.tradingplatform.domain.dto;

import com.tradingplatform.domain.enums.ErrorCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ErrorResponse (
    @NotNull(message="errorCode must be mentioned")
    ErrorCode errorCode,

    @NotBlank(message = "Reason for error must be mentioned")
    String message
) {
}