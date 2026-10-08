package com.tradingplatform.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CandleResponseJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void serialisesAdjustedCloseAsAdjcloseLikeTheCandleContract() throws Exception {
        CandleResponse candle = new CandleResponse(
            LocalDate.of(2026, 10, 2),
            new BigDecimal("10.00"),
            new BigDecimal("11.00"),
            new BigDecimal("9.00"),
            new BigDecimal("10.50"),
            new BigDecimal("10.40"),
            1200L,
            false
        );

        JsonNode json = mapper.readTree(mapper.writeValueAsString(candle));

        assertEquals(10.40, json.get("adjclose").asDouble());
        assertFalse(json.has("adjClose"));
    }
}
