package com.tradingplatform.api.controllers;

import com.tradingplatform.api.services.CandleAggregationService;
import com.tradingplatform.api.services.CandleStorageService;
import com.tradingplatform.api.services.InstrumentService;
import com.tradingplatform.domain.dto.CandleHistoryResponse;
import com.tradingplatform.domain.dto.CandleResponse;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.exception.CandleDataNotFoundException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.Instrument;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandleChartControllerTest {

    @Test
    void getCandleHistoryReturnsPaginatedResponse() {
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        FakeCandleAggregationService aggregationService = new FakeCandleAggregationService();
        FakeInstrumentService instrumentService = new FakeInstrumentService();
        CandleChartController controller = new CandleChartController(storageService, aggregationService, instrumentService);

        Long instrumentId = 1L;
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate to = LocalDate.parse("2026-10-05");

        Instrument instrument = Instrument.tradable(instrumentId, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD");
        List<CandleData> raw = List.of(
            candle(instrumentId, LocalDate.parse("2026-10-01"), "100", "101", "99", "100", 100L),
            candle(instrumentId, LocalDate.parse("2026-10-02"), "101", "103", "100", "102", 110L),
            candle(instrumentId, LocalDate.parse("2026-10-03"), "102", "104", "101", "103", 120L)
        );

        instrumentService.byId.put(instrumentId, instrument);
        storageService.candlesByRange = raw;
        aggregationService.nextAggregated = raw;

        ResponseEntity<CandleHistoryResponse> response = controller.getCandleHistory(instrumentId, from, to, "1m", 2, 0);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("AAPL", response.getBody().symbol());
        assertEquals(3, response.getBody().totalCount());
        assertTrue(response.getBody().hasMore());
        assertEquals(2, response.getBody().candles().size());
        assertEquals(1, storageService.getRangeCalls);
        assertEquals(1, aggregationService.aggregateCalls);
    }

    @Test
    void getCandleHistoryThrowsWhenInstrumentMissing() {
        CandleChartController controller = new CandleChartController(
            new FakeCandleStorageService(),
            new FakeCandleAggregationService(),
            new FakeInstrumentService());

        assertThrows(InstrumentNotFoundException.class,
            () -> controller.getCandleHistory(999L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), "1m", 10, 0));
    }

    @Test
    void getCandleHistoryThrowsForInvalidDateRange() {
        CandleChartController controller = new CandleChartController(
            new FakeCandleStorageService(),
            new FakeCandleAggregationService(),
            new FakeInstrumentService());

        assertThrows(CandleDataException.class,
            () -> controller.getCandleHistory(1L, LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-01"), "1m", 10, 0));
    }

    @Test
    void getCandleHistoryThrowsForInvalidPagination() {
        CandleChartController controller = new CandleChartController(
            new FakeCandleStorageService(),
            new FakeCandleAggregationService(),
            new FakeInstrumentService());

        assertThrows(CandleDataException.class,
            () -> controller.getCandleHistory(1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), "1m", 0, 0));

        assertThrows(CandleDataException.class,
            () -> controller.getCandleHistory(1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), "1m", 10, -1));
    }

    @Test
    void getLatestCandlesReturnsBoundedCount() {
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        FakeCandleAggregationService aggregationService = new FakeCandleAggregationService();
        FakeInstrumentService instrumentService = new FakeInstrumentService();
        CandleChartController controller = new CandleChartController(storageService, aggregationService, instrumentService);

        Long instrumentId = 1L;
        Instrument instrument = Instrument.tradable(instrumentId, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD");
        List<CandleData> latest = List.of(
            candle(instrumentId, LocalDate.parse("2026-10-04"), "101", "102", "100", "101", 100L),
            candle(instrumentId, LocalDate.parse("2026-10-05"), "102", "103", "101", "102", 110L)
        );

        instrumentService.byId.put(instrumentId, instrument);
        storageService.latestCandles = latest;

        ResponseEntity<CandleHistoryResponse> response = controller.getLatestCandles(instrumentId, 800);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().candles().size());
        assertFalse(response.getBody().hasMore());
        assertEquals(500, storageService.lastLatestCount);
    }

    @Test
    void getLatestCandlesThrowsForInvalidCount() {
        CandleChartController controller = new CandleChartController(
            new FakeCandleStorageService(),
            new FakeCandleAggregationService(),
            new FakeInstrumentService());

        assertThrows(CandleDataException.class, () -> controller.getLatestCandles(1L, 0));
    }

    @Test
    void getCandleForDateReturnsSingleCandle() {
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        CandleChartController controller = new CandleChartController(
            storageService,
            new FakeCandleAggregationService(),
            new FakeInstrumentService());

        Long instrumentId = 1L;
        LocalDate date = LocalDate.parse("2026-10-05");
        CandleData candle = candle(instrumentId, date, "102", "103", "101", "102", 110L);

        storageService.candleByDate = candle;

        ResponseEntity<CandleResponse> response = controller.getCandleForDate(instrumentId, date);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(date, response.getBody().date());
        assertEquals(new BigDecimal("102"), response.getBody().open());
    }

    @Test
    void getCandleForDateThrowsWhenMissing() {
        CandleChartController controller = new CandleChartController(
            new FakeCandleStorageService(),
            new FakeCandleAggregationService(),
            new FakeInstrumentService());

        Long instrumentId = 1L;
        LocalDate date = LocalDate.parse("2026-10-05");

        assertThrows(CandleDataNotFoundException.class, () -> controller.getCandleForDate(instrumentId, date));
    }

    private CandleData candle(Long instrumentId,
                              LocalDate date,
                              String open,
                              String high,
                              String low,
                              String close,
                              Long volume) {
        return new CandleData(
            instrumentId,
            date,
            new BigDecimal(open),
            new BigDecimal(high),
            new BigDecimal(low),
            new BigDecimal(close),
            new BigDecimal(close),
            volume,
            false
        );
    }

    private static final class FakeCandleStorageService extends CandleStorageService {
        List<CandleData> candlesByRange = List.of();
        List<CandleData> latestCandles = List.of();
        CandleData candleByDate;
        int getRangeCalls;
        int lastLatestCount;

        FakeCandleStorageService() {
            super(null);
        }

        @Override
        public List<CandleData> getCandles(Long instrumentId, LocalDate from, LocalDate to) {
            getRangeCalls++;
            return candlesByRange;
        }

        @Override
        public List<CandleData> getLatestCandles(Long instrumentId, int count) {
            lastLatestCount = count;
            return latestCandles;
        }

        @Override
        public CandleData getCandleByDate(Long instrumentId, LocalDate date) {
            return candleByDate;
        }
    }

    private static final class FakeCandleAggregationService extends CandleAggregationService {
        List<CandleData> nextAggregated = List.of();
        int aggregateCalls;

        @Override
        public List<CandleData> aggregateCandles(List<CandleData> candles, String interval) {
            aggregateCalls++;
            return nextAggregated;
        }
    }

    private static final class FakeInstrumentService extends InstrumentService {
        final Map<Long, Instrument> byId = new java.util.HashMap<>();

        FakeInstrumentService() {
            super(null);
        }

        @Override
        public Instrument getInstrumentById(Long id) {
            return byId.get(id);
        }
    }
}
