package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.CandleWatermarkMapper;
import com.tradingplatform.domain.model.CandleSyncWatermark;
import com.tradingplatform.domain.model.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CandleSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CandleSyncScheduler.class);
    private static final int BOOTSTRAP_MONTHS = 3;

    private final CandleSyncService candleSyncService;
    private final InstrumentService instrumentService;
    private final CandleWatermarkMapper watermarkMapper;

    @Value("${trading.candle.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    public CandleSyncScheduler(CandleSyncService candleSyncService,
                               InstrumentService instrumentService,
                               CandleWatermarkMapper watermarkMapper) {
        this.candleSyncService = candleSyncService;
        this.instrumentService = instrumentService;
        this.watermarkMapper = watermarkMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!bootstrapEnabled) {
            log.info("Candle data bootstrap is disabled");
            return;
        }

        List<Instrument> instruments = instrumentService.getAllInstruments();
        LocalDate fromDate = LocalDate.now().minusMonths(BOOTSTRAP_MONTHS);
        LocalDate toDate = LocalDate.now().minusDays(1);

        int bootstrappedCount = 0;
        int skippedCount = 0;

        for (Instrument instrument : instruments) {
            CandleSyncWatermark watermark = watermarkMapper.selectByInstrumentId(instrument.getId());
            if (watermark != null && watermark.isBootstrapCompleted()) {
                skippedCount++;
                continue;
            }

            try {
                candleSyncService.bootstrapCandleData(instrument.getId(), instrument.getSymbol(), fromDate, toDate);
                bootstrappedCount++;
            } catch (Exception ex) {
                log.error("Failed bootstrap for instrument {}", instrument.getSymbol(), ex);
            }
        }

        log.info("Candle bootstrap completed. bootstrapped={}, skipped={}", bootstrappedCount, skippedCount);
    }

    @Scheduled(cron = "${trading.candle.sync.cron:0 0 1 * * ?}")
    public void dailyDeltaSync() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Instrument> instruments = instrumentService.getTradableInstruments();

        int successCount = 0;
        int failureCount = 0;

        for (Instrument instrument : instruments) {
            try {
                candleSyncService.syncDeltaForDate(instrument.getId(), instrument.getSymbol(), yesterday);
                successCount++;
            } catch (Exception ex) {
                failureCount++;
                log.error("Failed daily delta sync for {}", instrument.getSymbol(), ex);
            }
        }

        log.info("Candle daily delta sync completed. success={}, failed={}", successCount, failureCount);
    }

    @Scheduled(cron = "${trading.candle.sync.retry.cron:0 0 2 * * ?}")
    public void retryFailedSyncs() {
        List<CandleSyncWatermark> failedSyncs = watermarkMapper.selectFailedSyncs();
        if (failedSyncs.isEmpty()) {
            return;
        }

        for (CandleSyncWatermark watermark : failedSyncs) {
            Instrument instrument = instrumentService.getInstrumentById(watermark.getInstrumentId());
            if (instrument == null) {
                continue;
            }

            LocalDate nextDate = watermark.getLastSyncedDate().plusDays(1);
            try {
                candleSyncService.syncDeltaForDate(instrument.getId(), instrument.getSymbol(), nextDate);
            } catch (Exception ex) {
                log.warn("Retry failed for instrumentId={}", instrument.getId(), ex);
            }
        }
    }

    @Scheduled(cron = "${trading.candle.purge.cron:0 0 3 ? * MON}")
    public void weeklyPurgeOldData() {
        try {
            int deletedCount = candleSyncService.purgeOldCandleData();
            log.info("Weekly candle purge completed. deletedCount={}", deletedCount);
        } catch (Exception ex) {
            log.error("Weekly candle purge failed", ex);
        }
    }
}
