package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily bar; field names follow the candle API contract (adjclose, not adjClose). */
public record CandleResponse(
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    @JsonProperty("adjclose") BigDecimal adjClose,
    Long volume,
    boolean synthetic
) {}
