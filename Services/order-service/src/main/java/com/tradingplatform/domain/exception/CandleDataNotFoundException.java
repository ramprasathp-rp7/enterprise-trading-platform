package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

import java.time.LocalDate;

public final class CandleDataNotFoundException extends DomainException {

    private final Long instrumentId;
    private final LocalDate date;

    public CandleDataNotFoundException(Long instrumentId, LocalDate date) {
        super(ErrorCode.CDL_404, String.format("Candle data not found for instrument %d on date %s", instrumentId, date));
        this.instrumentId = instrumentId;
        this.date = date;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getDate() {
        return date;
    }
}
