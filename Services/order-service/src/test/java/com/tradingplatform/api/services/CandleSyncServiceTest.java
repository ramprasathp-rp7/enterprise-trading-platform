package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.CandleWatermarkMapper;
import com.tradingplatform.domain.exception.CandleSyncException;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.CandleSyncWatermark;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandleSyncServiceTest {

    @Test
    void bootstrapSkipsWhenAlreadyCompleted() {
        FakeCandleDataService dataService = new FakeCandleDataService();
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        InMemoryCandleWatermarkMapper watermarkMapper = new InMemoryCandleWatermarkMapper();
        watermarkMapper.store.put(1L,
            new CandleSyncWatermark(10L, 1L, LocalDate.parse("2026-10-01"), CandleSyncWatermark.STATUS_COMPLETED, null, true));

        CandleSyncService service = new CandleSyncService(dataService, storageService, watermarkMapper);

        service.bootstrapCandleData(1L, "AAPL", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"));

        assertEquals(0, dataService.fetchCalls);
        assertEquals(0, storageService.storeCalls);
    }

    @Test
    void bootstrapStoresCandlesAndMarksCompleted() {
        FakeCandleDataService dataService = new FakeCandleDataService();
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        InMemoryCandleWatermarkMapper watermarkMapper = new InMemoryCandleWatermarkMapper();
        CandleSyncService service = new CandleSyncService(dataService, storageService, watermarkMapper);

        LocalDate from = LocalDate.parse("2026-09-01");
        LocalDate to = LocalDate.parse("2026-09-03");
        dataService.nextFetchResult = List.of(
            candle(1L, LocalDate.parse("2026-09-01")),
            candle(1L, LocalDate.parse("2026-09-02")),
            candle(1L, LocalDate.parse("2026-09-03"))
        );

        service.bootstrapCandleData(1L, "AAPL", from, to);

        assertEquals(1, dataService.fetchCalls);
        assertEquals(1, storageService.storeCalls);
        assertEquals(LocalDate.parse("2026-09-03"), watermarkMapper.store.get(1L).getLastSyncedDate());
        assertEquals(CandleSyncWatermark.STATUS_COMPLETED, watermarkMapper.store.get(1L).getSyncStatus());
    }

    @Test
    void bootstrapMarksFailedWhenFetchThrows() {
        FakeCandleDataService dataService = new FakeCandleDataService();
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        InMemoryCandleWatermarkMapper watermarkMapper = new InMemoryCandleWatermarkMapper();
        CandleSyncService service = new CandleSyncService(dataService, storageService, watermarkMapper);

        LocalDate from = LocalDate.parse("2026-09-01");
        LocalDate to = LocalDate.parse("2026-09-03");
        dataService.throwOnFetch = true;

        assertThrows(CandleSyncException.class, () -> service.bootstrapCandleData(1L, "AAPL", from, to));

        assertEquals(1, dataService.fetchCalls);
        assertEquals(0, storageService.storeCalls);
        assertEquals(CandleSyncWatermark.STATUS_FAILED, watermarkMapper.store.get(1L).getSyncStatus());
    }

    @Test
    void syncDeltaStoresCandlesAndMarksCompleted() {
        FakeCandleDataService dataService = new FakeCandleDataService();
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        InMemoryCandleWatermarkMapper watermarkMapper = new InMemoryCandleWatermarkMapper();
        CandleSyncService service = new CandleSyncService(dataService, storageService, watermarkMapper);

        LocalDate date = LocalDate.parse("2026-10-05");
        dataService.nextFetchResult = List.of(candle(1L, date));

        service.syncDeltaForDate(1L, "AAPL", date);

        assertEquals(1, dataService.fetchCalls);
        assertEquals(1, storageService.storeCalls);
        assertEquals(CandleSyncWatermark.STATUS_COMPLETED, watermarkMapper.store.get(1L).getSyncStatus());
    }

    @Test
    void syncDeltaMarksFailedOnError() {
        FakeCandleDataService dataService = new FakeCandleDataService();
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        InMemoryCandleWatermarkMapper watermarkMapper = new InMemoryCandleWatermarkMapper();
        CandleSyncService service = new CandleSyncService(dataService, storageService, watermarkMapper);

        LocalDate date = LocalDate.parse("2026-10-05");
        dataService.throwOnFetch = true;

        assertThrows(CandleSyncException.class, () -> service.syncDeltaForDate(1L, "AAPL", date));

        assertEquals(1, dataService.fetchCalls);
        assertEquals(0, storageService.storeCalls);
        assertEquals(CandleSyncWatermark.STATUS_FAILED, watermarkMapper.store.get(1L).getSyncStatus());
    }

    @Test
    void purgeOldCandleDataDelegatesToStorage() {
        FakeCandleDataService dataService = new FakeCandleDataService();
        FakeCandleStorageService storageService = new FakeCandleStorageService();
        InMemoryCandleWatermarkMapper watermarkMapper = new InMemoryCandleWatermarkMapper();
        CandleSyncService service = new CandleSyncService(dataService, storageService, watermarkMapper);
        storageService.purgeResult = 42;

        int deleted = service.purgeOldCandleData();

        assertEquals(42, deleted);
        assertEquals(1, storageService.purgeCalls);
        assertEquals(0, dataService.fetchCalls);
    }

    private CandleData candle(Long instrumentId, LocalDate date) {
        return new CandleData(
            instrumentId,
            date,
            new BigDecimal("100"),
            new BigDecimal("101"),
            new BigDecimal("99"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            10L,
            false
        );
    }

    private static final class FakeCandleDataService extends CandleDataService {
        int fetchCalls;
        boolean throwOnFetch;
        List<CandleData> nextFetchResult = List.of();

        FakeCandleDataService() {
            super(null, null, "http://unused", "unused");
        }

        @Override
        public List<CandleData> fetchCandlesForSymbol(Long instrumentId, String symbol, LocalDate from, LocalDate to) {
            fetchCalls++;
            if (throwOnFetch) {
                throw new RuntimeException("fetch failed");
            }
            return nextFetchResult;
        }
    }

    private static final class FakeCandleStorageService extends CandleStorageService {
        int storeCalls;
        int purgeCalls;
        int purgeResult;

        FakeCandleStorageService() {
            super(null);
        }

        @Override
        public void storeCandles(List<CandleData> candles) {
            storeCalls++;
        }

        @Override
        public int purgeOldCandleData() {
            purgeCalls++;
            return purgeResult;
        }
    }

    private static final class InMemoryCandleWatermarkMapper implements CandleWatermarkMapper {
        final Map<Long, CandleSyncWatermark> store = new HashMap<>();
        final List<CandleSyncWatermark> inserted = new ArrayList<>();
        final List<CandleSyncWatermark> updated = new ArrayList<>();

        @Override
        public void insertWatermark(CandleSyncWatermark watermark) {
            inserted.add(watermark);
            store.put(watermark.getInstrumentId(), watermark);
        }

        @Override
        public void updateWatermark(CandleSyncWatermark watermark) {
            updated.add(watermark);
            store.put(watermark.getInstrumentId(), watermark);
        }

        @Override
        public CandleSyncWatermark selectByInstrumentId(Long instrumentId) {
            return store.get(instrumentId);
        }

        @Override
        public List<CandleSyncWatermark> selectFailedSyncs() {
            return store.values().stream()
                .filter(w -> CandleSyncWatermark.STATUS_FAILED.equals(w.getSyncStatus()))
                .toList();
        }
    }
}
