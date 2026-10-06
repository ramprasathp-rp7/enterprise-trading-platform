package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.CandleDataMapper;
import com.tradingplatform.domain.model.CandleData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class CandleStorageService {

    private final CandleDataMapper candleDataMapper;

    public CandleStorageService(CandleDataMapper candleDataMapper) {
        this.candleDataMapper = candleDataMapper;
    }

    @Transactional
    public void storeCandles(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }
        candleDataMapper.upsertBatch(candles);
    }

    public boolean candleExists(Long instrumentId, LocalDate date) {
        return candleDataMapper.countByInstrumentAndDate(instrumentId, date) > 0;
    }

    @Transactional
    public void deleteCandle(Long instrumentId, LocalDate date) {
        candleDataMapper.deleteByInstrumentAndDate(instrumentId, date);
    }

    public List<CandleData> getCandles(Long instrumentId, LocalDate from, LocalDate to) {
        return candleDataMapper.selectByInstrumentDateRange(instrumentId, from, to);
    }

    public List<CandleData> getLatestCandles(Long instrumentId, int count) {
        return candleDataMapper.selectLatestByInstrument(instrumentId, count)
            .stream()
            .sorted(Comparator.comparing(CandleData::getDate))
            .toList();
    }

    public CandleData getCandleByDate(Long instrumentId, LocalDate date) {
        return candleDataMapper.selectByInstrumentAndDate(instrumentId, date);
    }

    @Transactional
    public int purgeOldCandleData() {
        Long deletedCount = candleDataMapper.purgeOldData();
        return deletedCount != null ? deletedCount.intValue() : 0;
    }
}
