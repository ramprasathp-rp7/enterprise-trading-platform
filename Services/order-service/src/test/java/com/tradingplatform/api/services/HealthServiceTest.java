package com.tradingplatform.api.services;

import com.tradingplatform.domain.dto.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HealthService Tests")
class HealthServiceTest {

    @Test
    @DisplayName("Public /health reports the last known quota without spending any")
    void healthDoesNotCallFauxnance() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        FauxnanceApiClient fauxnance = mock(FauxnanceApiClient.class);
        when(fauxnance.getQuotaRemaining()).thenReturn(1234);

        HealthService health = new HealthService(jdbc, fauxnance);
        HealthResponse first = health.getHealth();
        health.getHealth();

        // A field read only: no /usage, /health or quote call, however often /health is polled
        verify(fauxnance, times(2)).getQuotaRemaining();
        verifyNoMoreInteractions(fauxnance);
        assertEquals("ok", first.getStatus());
        assertEquals(1234, first.getDependencies().stream()
            .filter(d -> d.getName().equals("fauxnance")).findFirst().orElseThrow().getQuotaRemaining());
    }
}
