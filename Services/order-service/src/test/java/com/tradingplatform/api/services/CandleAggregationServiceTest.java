package com.tradingplatform.api.services;

import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.model.CandleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandleAggregationServiceTest {

    private CandleAggregationService service;

    @BeforeEach
    void setUp() {
        service = new CandleAggregationService();
    }

    @Test
    void aggregateCandlesReturnsEmptyForNullOrEmptyInput() {
        assertTrue(service.aggregateCandles(null, "1m").isEmpty());
        assertTrue(service.aggregateCandles(List.of(), "1m").isEmpty());
    }

    @Test
    void aggregateCandlesReturnsOriginalListFor1mInterval() {
        List<CandleData> candles = List.of(
            candle(1L, LocalDate.parse("2026-10-01"), "100", "105", "99", "103", "103", 1000L, false)
        );

        List<CandleData> result = service.aggregateCandles(candles, "1m");

        assertSame(candles, result);
    }

    @Test
    void aggregateCandlesSupportsBlankOrNullIntervalAs1m() {
        List<CandleData> candles = List.of(
            candle(1L, LocalDate.parse("2026-10-01"), "100", "105", "99", "103", "103", 1000L, false)
        );

        assertSame(candles, service.aggregateCandles(candles, ""));
        assertSame(candles, service.aggregateCandles(candles, null));
    }

    @Test
    void aggregateCandlesAggregatesThreeDayWindowCorrectly() {
        List<CandleData> candles = List.of(
            candle(1L, LocalDate.parse("2026-10-01"), "100", "106", "98", "102", "102", 10L, false),
            candle(1L, LocalDate.parse("2026-10-02"), "102", "108", "101", "107", "107", 20L, true),
            candle(1L, LocalDate.parse("2026-10-03"), "107", "109", "103", "104", "104", 30L, false)
        );

        List<CandleData> result = service.aggregateCandles(candles, "3m");

        assertEquals(1, result.size());
        CandleData c = result.get(0);
        assertEquals(LocalDate.parse("2026-10-03"), c.getDate());
        assertEquals(new BigDecimal("100"), c.getOpen());
        assertEquals(new BigDecimal("109"), c.getHigh());
        assertEquals(new BigDecimal("98"), c.getLow());
        assertEquals(new BigDecimal("104"), c.getClose());
        assertEquals(new BigDecimal("104"), c.getAdjClose());
        assertEquals(60L, c.getVolume());
        assertTrue(c.isSynthetic());
    }

    @Test
    void aggregateCandlesThrowsForUnsupportedInterval() {
        List<CandleData> candles = List.of(
            candle(1L, LocalDate.parse("2026-10-01"), "100", "101", "99", "100", "100", 10L, false)
        );

        assertThrows(CandleDataException.class, () -> service.aggregateCandles(candles, "2m"));
    }

    private CandleData candle(Long instrumentId,
                              LocalDate date,
                              String open,
                              String high,
                              String low,
                              String close,
                              String adjClose,
                              Long volume,
                              boolean synthetic) {
        return new CandleData(
            instrumentId,
            date,
            new BigDecimal(open),
            new BigDecimal(high),
            new BigDecimal(low),
            new BigDecimal(close),
            new BigDecimal(adjClose),
            volume,
            synthetic
        );
    }
}
