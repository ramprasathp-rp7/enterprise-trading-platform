package com.tradingplatform.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandleResponse(
    LocalDate date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal adjClose,
    Long volume,
    boolean synthetic
) {}
