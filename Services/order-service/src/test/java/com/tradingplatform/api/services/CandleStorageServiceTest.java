package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.CandleDataMapper;
import com.tradingplatform.domain.model.CandleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandleStorageServiceTest {

    private InMemoryCandleDataMapper mapper;
    private CandleStorageService service;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryCandleDataMapper();
        service = new CandleStorageService(mapper);
    }

    @Test
    void storeCandlesPersistsBatchAndExistsReturnsTrue() {
        CandleData candle = candle(1L, LocalDate.parse("2026-10-01"), 100L);

        service.storeCandles(List.of(candle));

        assertTrue(service.candleExists(1L, LocalDate.parse("2026-10-01")));
        assertEquals(1, mapper.rows.size());
    }

    @Test
    void storeCandlesIgnoresNullAndEmpty() {
        service.storeCandles(null);
        service.storeCandles(List.of());

        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void getCandlesReturnsDateRangeAscending() {
        mapper.upsertBatch(List.of(
            candle(1L, LocalDate.parse("2026-10-01"), 100L),
            candle(1L, LocalDate.parse("2026-10-03"), 200L),
            candle(1L, LocalDate.parse("2026-10-02"), 150L)
        ));

        List<CandleData> candles = service.getCandles(1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"));

        assertEquals(3, candles.size());
        assertEquals(LocalDate.parse("2026-10-01"), candles.get(0).getDate());
        assertEquals(LocalDate.parse("2026-10-02"), candles.get(1).getDate());
        assertEquals(LocalDate.parse("2026-10-03"), candles.get(2).getDate());
    }

    @Test
    void getLatestCandlesReturnsAscendingEvenWhenMapperReturnsDescending() {
        mapper.upsertBatch(List.of(
            candle(1L, LocalDate.parse("2026-10-01"), 100L),
            candle(1L, LocalDate.parse("2026-10-02"), 110L),
            candle(1L, LocalDate.parse("2026-10-03"), 120L)
        ));

        List<CandleData> latest = service.getLatestCandles(1L, 2);

        assertEquals(2, latest.size());
        assertEquals(LocalDate.parse("2026-10-02"), latest.get(0).getDate());
        assertEquals(LocalDate.parse("2026-10-03"), latest.get(1).getDate());
    }

    @Test
    void getAndDeleteCandleByDateWorks() {
        CandleData candle = candle(1L, LocalDate.parse("2026-10-01"), 100L);
        mapper.upsertCandle(candle);

        assertEquals(LocalDate.parse("2026-10-01"), service.getCandleByDate(1L, LocalDate.parse("2026-10-01")).getDate());

        service.deleteCandle(1L, LocalDate.parse("2026-10-01"));

        assertFalse(service.candleExists(1L, LocalDate.parse("2026-10-01")));
    }

    @Test
    void purgeOldCandleDataReturnsDeletedCount() {
        mapper.purgeReturn = 7L;

        int deleted = service.purgeOldCandleData();

        assertEquals(7, deleted);
    }

    private CandleData candle(Long instrumentId, LocalDate date, Long volume) {
        return new CandleData(
            instrumentId,
            date,
            new BigDecimal("100"),
            new BigDecimal("101"),
            new BigDecimal("99"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            volume,
            false
        );
    }

    private static final class InMemoryCandleDataMapper implements CandleDataMapper {
        private final List<CandleData> rows = new ArrayList<>();
        private Long purgeReturn;

        @Override
        public void upsertCandle(CandleData candle) {
            deleteByInstrumentAndDate(candle.getInstrumentId(), candle.getDate());
            rows.add(candle);
        }

        @Override
        public void upsertBatch(List<CandleData> candles) {
            for (CandleData candle : candles) {
                upsertCandle(candle);
            }
        }

        @Override
        public CandleData selectByInstrumentAndDate(Long instrumentId, LocalDate date) {
            return rows.stream()
                .filter(c -> c.getInstrumentId().equals(instrumentId) && c.getDate().equals(date))
                .findFirst()
                .orElse(null);
        }

        @Override
        public List<CandleData> selectByInstrumentDateRange(Long instrumentId, LocalDate from, LocalDate to) {
            return rows.stream()
                .filter(c -> c.getInstrumentId().equals(instrumentId))
                .filter(c -> !c.getDate().isBefore(from) && !c.getDate().isAfter(to))
                .sorted(Comparator.comparing(CandleData::getDate))
                .toList();
        }

        @Override
        public List<CandleData> selectLatestByInstrument(Long instrumentId, int count) {
            return rows.stream()
                .filter(c -> c.getInstrumentId().equals(instrumentId))
                .sorted(Comparator.comparing(CandleData::getDate).reversed())
                .limit(count)
                .toList();
        }

        @Override
        public void deleteByInstrumentAndDate(Long instrumentId, LocalDate date) {
            rows.removeIf(c -> c.getInstrumentId().equals(instrumentId) && c.getDate().equals(date));
        }

        @Override
        public long countByInstrumentAndDate(Long instrumentId, LocalDate date) {
            return rows.stream().filter(c -> c.getInstrumentId().equals(instrumentId) && c.getDate().equals(date)).count();
        }

        @Override
        public Long purgeOldData() {
            return purgeReturn;
        }
    }
}
