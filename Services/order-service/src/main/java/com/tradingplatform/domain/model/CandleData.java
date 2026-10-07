package com.tradingplatform.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class CandleData {

    private final Long id;
    private final Long instrumentId;
    private final LocalDate date;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final BigDecimal adjClose;
    private final Long volume;
    private final boolean synthetic;
    private final Instant createdAt;
    private final Instant updatedAt;

    public CandleData(Long id,
                      Long instrumentId,
                      LocalDate date,
                      BigDecimal open,
                      BigDecimal high,
                      BigDecimal low,
                      BigDecimal close,
                      BigDecimal adjClose,
                      Long volume,
                      boolean synthetic,
                      Instant createdAt,
                      Instant updatedAt) {
        this.id = id;
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.date = Objects.requireNonNull(date, "date");
        this.open = Objects.requireNonNull(open, "open");
        this.high = Objects.requireNonNull(high, "high");
        this.low = Objects.requireNonNull(low, "low");
        this.close = Objects.requireNonNull(close, "close");
        this.adjClose = Objects.requireNonNull(adjClose, "adjClose");
        this.volume = Objects.requireNonNull(volume, "volume");
        this.synthetic = synthetic;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        if (this.high.compareTo(this.low) < 0) {
            throw new IllegalArgumentException("high must be >= low");
        }
        if (this.high.compareTo(this.open) < 0 || this.high.compareTo(this.close) < 0) {
            throw new IllegalArgumentException("high must be >= open and close");
        }
        if (this.low.compareTo(this.open) > 0 || this.low.compareTo(this.close) > 0) {
            throw new IllegalArgumentException("low must be <= open and close");
        }
        if (this.volume < 0) {
            throw new IllegalArgumentException("volume must be >= 0");
        }
    }

    public CandleData(Long instrumentId,
                      LocalDate date,
                      BigDecimal open,
                      BigDecimal high,
                      BigDecimal low,
                      BigDecimal close,
                      BigDecimal adjClose,
                      Long volume,
                      boolean synthetic) {
        this(null, instrumentId, date, open, high, low, close, adjClose, volume, synthetic, null, null);
    }

    public Long getId() {
        return id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getAdjClose() {
        return adjClose;
    }

    public Long getVolume() {
        return volume;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
