package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.CandleWatermarkMapper;
import com.tradingplatform.domain.exception.CandleSyncException;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.CandleSyncWatermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CandleSyncService {

    private static final Logger log = LoggerFactory.getLogger(CandleSyncService.class);

    private final CandleDataService candleDataService;
    private final CandleStorageService candleStorageService;
    private final CandleWatermarkMapper candleWatermarkMapper;

    public CandleSyncService(CandleDataService candleDataService,
                             CandleStorageService candleStorageService,
                             CandleWatermarkMapper candleWatermarkMapper) {
        this.candleDataService = candleDataService;
        this.candleStorageService = candleStorageService;
        this.candleWatermarkMapper = candleWatermarkMapper;
    }

    @Transactional
    public void bootstrapCandleData(Long instrumentId, String symbol, LocalDate fromDate, LocalDate toDate) {
        CandleSyncWatermark existing = candleWatermarkMapper.selectByInstrumentId(instrumentId);
        if (existing != null && existing.isBootstrapCompleted()) {
            return;
        }

        upsertWatermark(instrumentId, fromDate, CandleSyncWatermark.STATUS_IN_PROGRESS, false);

        try {
            List<CandleData> candles = candleDataService.fetchCandlesForSymbol(instrumentId, symbol, fromDate, toDate);
            candleStorageService.storeCandles(candles);
            LocalDate syncedDate = candles.isEmpty() ? toDate : candles.get(candles.size() - 1).getDate();
            upsertWatermark(instrumentId, syncedDate, CandleSyncWatermark.STATUS_COMPLETED, true);
        } catch (Exception ex) {
            upsertWatermark(instrumentId, fromDate, CandleSyncWatermark.STATUS_FAILED, false);
            throw new CandleSyncException("Bootstrap sync failed for instrument " + instrumentId, ex);
        }
    }

    @Transactional
    public void syncDeltaForDate(Long instrumentId, String symbol, LocalDate date) {
        upsertWatermark(instrumentId, date, CandleSyncWatermark.STATUS_IN_PROGRESS, true);

        try {
            List<CandleData> candles = candleDataService.fetchCandlesForSymbol(instrumentId, symbol, date, date);
            candleStorageService.storeCandles(candles);
            upsertWatermark(instrumentId, date, CandleSyncWatermark.STATUS_COMPLETED, true);
        } catch (Exception ex) {
            upsertWatermark(instrumentId, date, CandleSyncWatermark.STATUS_FAILED, true);
            throw new CandleSyncException("Delta sync failed for instrument " + instrumentId + " on " + date, ex);
        }
    }

    @Transactional
    public int purgeOldCandleData() {
        return candleStorageService.purgeOldCandleData();
    }

    private void upsertWatermark(Long instrumentId, LocalDate lastSyncedDate, String status, boolean bootstrapCompleted) {
        CandleSyncWatermark existing = candleWatermarkMapper.selectByInstrumentId(instrumentId);
        CandleSyncWatermark watermark = new CandleSyncWatermark(instrumentId, lastSyncedDate, status, bootstrapCompleted);

        if (existing == null) {
            candleWatermarkMapper.insertWatermark(watermark);
        } else {
            candleWatermarkMapper.updateWatermark(watermark);
        }

        log.debug("Updated candle watermark: instrumentId={}, status={}, date={}, bootstrapCompleted={}",
            instrumentId, status, lastSyncedDate, bootstrapCompleted);
    }
}
