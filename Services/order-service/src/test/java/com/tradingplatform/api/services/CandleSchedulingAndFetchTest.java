package com.tradingplatform.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.CandleWatermarkMapper;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.exception.CandleSyncException;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.CandleSyncWatermark;
import com.tradingplatform.domain.model.Instrument;

/**
 * The candle jobs (bootstrap, daily delta, retries, purge) and the Fauxnance candle fetch.
 */
class CandleSchedulingAndFetchTest {

    private static final Instrument AAPL = new Instrument(1L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
    private static final Instrument MSFT = new Instrument(2L, "MSFT", "Microsoft", AssetClass.EQUITY, "USD", true);

    private final CandleSyncService sync = mock(CandleSyncService.class);
    private final InstrumentService instruments = mock(InstrumentService.class);
    private final CandleWatermarkMapper watermarks = mock(CandleWatermarkMapper.class);

    private CandleSyncScheduler scheduler(boolean bootstrapEnabled) {
        CandleSyncScheduler scheduler = new CandleSyncScheduler(sync, instruments, watermarks);
        ReflectionTestUtils.setField(scheduler, "bootstrapEnabled", bootstrapEnabled);
        return scheduler;
    }

    @Test
    void bootstrapSkipsWhenDisabled() {
        scheduler(false).bootstrapOnStartup();
        verifyNoInteractions(instruments, sync);
    }

    @Test
    void bootstrapLoadsThreeMonthsForInstrumentsNotYetBootstrappedAndCarriesOnAfterAFailure() {
        Instrument done = new Instrument(3L, "PFE", "Pfizer", AssetClass.EQUITY, "USD", true);
        when(instruments.getAllInstruments()).thenReturn(List.of(AAPL, MSFT, done));
        when(watermarks.selectByInstrumentId(3L)).thenReturn(new CandleSyncWatermark(3L, LocalDate.now(), "SUCCESS", true));
        when(watermarks.selectByInstrumentId(2L)).thenReturn(new CandleSyncWatermark(2L, LocalDate.now(), "FAILED", false));
        doThrow(new CandleSyncException("down")).when(sync).bootstrapCandleData(eq(1L), anyString(), any(), any());

        scheduler(true).bootstrapOnStartup();

        LocalDate from = LocalDate.now().minusMonths(3);
        LocalDate to = LocalDate.now().minusDays(1);
        verify(sync).bootstrapCandleData(1L, "AAPL", from, to);
        verify(sync).bootstrapCandleData(2L, "MSFT", from, to);
        verify(sync, never()).bootstrapCandleData(eq(3L), anyString(), any(), any());
    }

    @Test
    void dailyDeltaSyncsYesterdayForEveryTradableInstrument() {
        when(instruments.getTradableInstruments()).thenReturn(List.of(AAPL, MSFT));
        doThrow(new CandleSyncException("down")).when(sync).syncDeltaForDate(eq(1L), anyString(), any());

        scheduler(true).dailyDeltaSync();

        verify(sync).syncDeltaForDate(2L, "MSFT", LocalDate.now().minusDays(1));
    }

    @Test
    void retriesStartTheDayAfterTheLastSyncAndSkipRemovedInstruments() {
        LocalDate last = LocalDate.of(2026, 10, 5);
        when(watermarks.selectFailedSyncs()).thenReturn(List.of(
            new CandleSyncWatermark(1L, last, "FAILED", true),
            new CandleSyncWatermark(2L, last, "FAILED", true),
            new CandleSyncWatermark(9L, last, "FAILED", true)));
        when(instruments.getInstrumentById(1L)).thenReturn(AAPL);
        when(instruments.getInstrumentById(2L)).thenReturn(MSFT);
        doThrow(new CandleSyncException("still down")).when(sync).syncDeltaForDate(eq(1L), anyString(), any());

        scheduler(true).retryFailedSyncs();

        verify(sync).syncDeltaForDate(1L, "AAPL", LocalDate.of(2026, 10, 6));
        verify(sync).syncDeltaForDate(2L, "MSFT", LocalDate.of(2026, 10, 6));
        verify(sync, never()).syncDeltaForDate(eq(9L), anyString(), any());
    }

    @Test
    void retriesDoNothingWhenNothingFailed() {
        when(watermarks.selectFailedSyncs()).thenReturn(List.of());
        scheduler(true).retryFailedSyncs();
        verifyNoInteractions(instruments, sync);
    }

    @Test
    void weeklyPurgeSwallowsFailures() {
        when(sync.purgeOldCandleData()).thenReturn(12).thenThrow(new IllegalStateException("db"));
        CandleSyncScheduler scheduler = scheduler(true);

        scheduler.weeklyPurgeOldData();
        scheduler.weeklyPurgeOldData();

        verify(sync, org.mockito.Mockito.times(2)).purgeOldCandleData();
    }

    // ===== CandleDataService =====

    private final RestTemplate rest = mock(RestTemplate.class);
    private final CandleDataService candles = new CandleDataService(rest, new ObjectMapper(), "https://fauxnance.test", "key");

    private void respond(ResponseEntity<String> response) {
        when(rest.exchange(contains("/candles/"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(response);
    }

    @Test
    void fetchParsesCandlesOldestFirstFromEitherPayloadShape() {
        respond(ResponseEntity.ok("""
            {"data":{"candles":[
              {"date":"2026-10-08","open":"10","high":"12","low":"9","close":"11","adjclose":"11","volume":100},
              {"date":"2026-10-07","open":"9","high":"10","low":"8","close":"10","adjClose":"10","synthetic":true}
            ]}}"""));

        List<CandleData> result = candles.fetchCandlesForSymbol(1L, "BRK.B", LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8));

        assertThat(result).extracting(CandleData::getDate)
            .containsExactly(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8));
        assertThat(result.get(0).isSynthetic()).isTrue();
        assertThat(result.get(0).getVolume()).isZero();
        assertThat(result.get(1).getClose()).isEqualByComparingTo("11");
        assertThat(candles.parseCandlesResponse(1L, "{\"candles\":[{\"date\":\"2026-10-08\",\"open\":1,\"high\":1,\"low\":1,\"close\":1,\"adjclose\":1}]}"))
            .hasSize(1);
    }

    @Test
    void emptyOrShapelessResponsesHaveNoCandles() {
        assertThat(candles.parseCandlesResponse(1L, null)).isEmpty();
        assertThat(candles.parseCandlesResponse(1L, "  ")).isEmpty();
        assertThat(candles.parseCandlesResponse(1L, "{\"data\":{}}")).isEmpty();
        assertThatThrownBy(() -> candles.parseCandlesResponse(1L, "{\"candles\":[{\"date\":\"nope\"}]}"))
            .isInstanceOf(CandleDataException.class);
    }

    @Test
    void fetchFailuresBecomeSyncErrors() {
        respond(new ResponseEntity<>("{}", HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> candles.fetchCandlesForSymbol(1L, "AAPL", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)))
            .isInstanceOf(CandleSyncException.class).hasMessageContaining("502");

        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> candles.fetchCandlesForSymbol(1L, "AAPL", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)))
            .isInstanceOf(CandleSyncException.class).hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void dateRangesAreValidated() {
        LocalDate day = LocalDate.of(2026, 10, 9);
        assertThatThrownBy(() -> candles.validateDateRange(null, day)).isInstanceOf(CandleDataException.class);
        assertThatThrownBy(() -> candles.validateDateRange(day, null)).isInstanceOf(CandleDataException.class);
        assertThatThrownBy(() -> candles.validateDateRange(day, day.minusDays(1))).isInstanceOf(CandleDataException.class);
        assertThatThrownBy(() -> candles.validateDateRange(day.minusYears(11), day)).hasMessageContaining("10 years");
        candles.validateDateRange(day, day);
        verify(rest, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }
}
