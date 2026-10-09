package com.tradingplatform.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.dto.HealthResponse;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.domain.model.Instrument;

/**
 * Health, instrument lookup and the fee schedule.
 */
class SmallServicesTest {

    @Test
    void healthIsOkWhenPostgresAnswersAndShowsTheQuota() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FauxnanceApiClient fauxnance = mock(FauxnanceApiClient.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(fauxnance.getQuotaRemaining()).thenReturn(1900);

        HealthResponse health = new HealthService(jdbc, fauxnance).getHealth();

        assertThat(health.getStatus()).isEqualTo("ok");
        assertThat(health.getDependencies()).extracting(HealthResponse.DependencyStatus::getName)
            .containsExactly("postgres", "fauxnance");
        assertThat(health.getDependencies().get(1).getQuotaRemaining()).isEqualTo(1900);
    }

    @Test
    void healthIsDegradedWhenADependencyFails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FauxnanceApiClient fauxnance = mock(FauxnanceApiClient.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("down"));
        when(fauxnance.getQuotaRemaining()).thenThrow(new IllegalStateException("boom"));

        HealthResponse health = new HealthService(jdbc, fauxnance).getHealth();

        assertThat(health.getStatus()).isEqualTo("degraded");
        assertThat(health.getDependencies()).allMatch(d -> "degraded".equals(d.getStatus()));
    }

    @Test
    void instrumentLookupsNeverReturnNullLists() {
        InstrumentMapper mapper = mock(InstrumentMapper.class);
        Instrument aapl = Instrument.tradable(1L, "AAPL", "Apple", AssetClass.EQUITY, "USD");
        InstrumentService service = new InstrumentService(mapper);
        when(mapper.findById(1L)).thenReturn(aapl);
        when(mapper.findAllInstruments()).thenReturn(null).thenReturn(List.of(aapl));
        when(mapper.findTradableInstruments()).thenReturn(null).thenReturn(List.of(aapl));

        assertThat(service.getInstrumentById(1L)).isSameAs(aapl);
        assertThat(service.getAllInstruments()).isEmpty();
        assertThat(service.getAllInstruments()).containsExactly(aapl);
        assertThat(service.getTradableInstruments()).isEmpty();
        assertThat(service.getTradableInstruments()).containsExactly(aapl);
    }

    @Test
    void feeScheduleListsEveryAssetClass() {
        AssetFeeMapper mapper = mock(AssetFeeMapper.class);
        when(mapper.findAll()).thenReturn(List.of(
            new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.001")),
            new AssetFeeStructure("FX", new BigDecimal("1.00"), new BigDecimal("0.0002"))));

        assertThat(new FeeService(mapper).getAllFees()).hasSize(2);
    }
}
