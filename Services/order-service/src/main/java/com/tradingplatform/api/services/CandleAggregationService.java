package com.tradingplatform.api.services;

import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.model.CandleData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandleAggregationService {

    public List<CandleData> aggregateCandles(List<CandleData> candles, String interval) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        String normalizedInterval = (interval == null || interval.isBlank()) ? "1m" : interval;

        int windowSize = switch (normalizedInterval) {
            case "1m" -> 1;
            case "3m" -> 3;
            case "5m" -> 5;
            default -> throw new CandleDataException("Unsupported interval: " + interval);
        };

        if (windowSize == 1) {
            return candles;
        }

        List<CandleData> aggregated = new ArrayList<>();
        for (int i = 0; i < candles.size(); i += windowSize) {
            int end = Math.min(i + windowSize, candles.size());
            List<CandleData> window = candles.subList(i, end);
            aggregated.add(aggregate(window));
        }
        return aggregated;
    }

    CandleData aggregate(List<CandleData> window) {
        CandleData first = window.get(0);
        CandleData last = window.get(window.size() - 1);

        BigDecimal high = window.stream().map(CandleData::getHigh).max(BigDecimal::compareTo).orElse(first.getHigh());
        BigDecimal low = window.stream().map(CandleData::getLow).min(BigDecimal::compareTo).orElse(first.getLow());
        long volume = window.stream().mapToLong(CandleData::getVolume).sum();
        boolean synthetic = window.stream().anyMatch(CandleData::isSynthetic);
        LocalDate date = last.getDate();

        return new CandleData(
            first.getInstrumentId(),
            date,
            first.getOpen(),
            high,
            low,
            last.getClose(),
            last.getAdjClose(),
            volume,
            synthetic
        );
    }
}
