package com.tradingplatform.api.controllers;

import com.tradingplatform.api.services.CandleAggregationService;
import com.tradingplatform.api.services.CandleStorageService;
import com.tradingplatform.api.services.InstrumentService;
import com.tradingplatform.domain.dto.CandleHistoryResponse;
import com.tradingplatform.domain.dto.CandleResponse;
import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.exception.CandleDataNotFoundException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.Instrument;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/instruments/{instrumentId}/candles")
public class CandleChartController {

    private final CandleStorageService storageService;
    private final CandleAggregationService aggregationService;
    private final InstrumentService instrumentService;

    public CandleChartController(CandleStorageService storageService,
                                 CandleAggregationService aggregationService,
                                 InstrumentService instrumentService) {
        this.storageService = storageService;
        this.aggregationService = aggregationService;
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public ResponseEntity<CandleHistoryResponse> getCandleHistory(
        @PathVariable Long instrumentId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "1m") String interval,
        @RequestParam(defaultValue = "500") int limit,
        @RequestParam(defaultValue = "0") int offset) {

        validateDateRange(from, to);
        validatePagination(limit, offset);

        Instrument instrument = instrumentService.getInstrumentById(instrumentId);
        if (instrument == null) {
            throw new InstrumentNotFoundException(String.valueOf(instrumentId));
        }

        int boundedLimit = Math.min(limit, 2000);
        List<CandleData> candles = storageService.getCandles(instrumentId, from, to);
        candles = aggregationService.aggregateCandles(candles, interval);

        List<CandleResponse> paginated = candles.stream()
            .skip(offset)
            .limit(boundedLimit)
            .map(this::toResponse)
            .toList();

        CandleHistoryResponse response = new CandleHistoryResponse(
            instrument.getSymbol(),
            instrument.getName(),
            instrument.getAssetClass().name(),
            paginated,
            candles.size(),
            (offset + boundedLimit) < candles.size()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    public ResponseEntity<CandleHistoryResponse> getLatestCandles(
        @PathVariable Long instrumentId,
        @RequestParam(defaultValue = "100") int count) {

        if (count < 1) {
            throw new CandleDataException("count must be at least 1");
        }

        Instrument instrument = instrumentService.getInstrumentById(instrumentId);
        if (instrument == null) {
            throw new InstrumentNotFoundException(String.valueOf(instrumentId));
        }

        int boundedCount = Math.min(count, 500);
        List<CandleResponse> candles = storageService.getLatestCandles(instrumentId, boundedCount)
            .stream()
            .map(this::toResponse)
            .toList();

        CandleHistoryResponse response = new CandleHistoryResponse(
            instrument.getSymbol(),
            instrument.getName(),
            instrument.getAssetClass().name(),
            candles,
            candles.size(),
            false
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{date}")
    public ResponseEntity<CandleResponse> getCandleForDate(
        @PathVariable Long instrumentId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        CandleData candle = storageService.getCandleByDate(instrumentId, date);
        if (candle == null) {
            throw new CandleDataNotFoundException(instrumentId, date);
        }
        return ResponseEntity.ok(toResponse(candle));
    }

    private CandleResponse toResponse(CandleData candle) {
        return new CandleResponse(
            candle.getDate(),
            candle.getOpen(),
            candle.getHigh(),
            candle.getLow(),
            candle.getClose(),
            candle.getAdjClose(),
            candle.getVolume(),
            candle.isSynthetic()
        );
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new CandleDataException("from date must be before or equal to to date");
        }
        if (ChronoUnit.DAYS.between(from, to) > 3650) {
            throw new CandleDataException("Date range cannot exceed 10 years");
        }
    }

    private void validatePagination(int limit, int offset) {
        if (limit < 1) {
            throw new CandleDataException("limit must be at least 1");
        }
        if (offset < 0) {
            throw new CandleDataException("offset must be at least 0");
        }
    }
}
