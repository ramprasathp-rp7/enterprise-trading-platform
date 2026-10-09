package com.tradingplatform.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class FauxnanceSymbolLookupTest {

    private RestTemplate restTemplate;
    private FauxnanceApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new FauxnanceApiClient(restTemplate, new ObjectMapper(), "test-key", "https://fauxnance.test");
    }

    @Test
    void findSymbol_readsTheRegistryEntry() {
        when(restTemplate.exchange(eq("https://fauxnance.test/symbols/INFY.NS"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("""
                {"data": {"symbol": "INFY.NS", "name": "Infosys Limited", "type": "equity", "exchange": "NSE",
                          "currency": "INR", "active": true, "coverage": {}},
                 "meta": {}}
                """));

        FauxnanceApiClient.SymbolInfo info = client.findSymbol("INFY.NS").orElseThrow();

        assertEquals("INFY.NS", info.symbol());
        assertEquals("Infosys Limited", info.name());
        assertEquals("equity", info.type());
        assertEquals("NSE", info.exchange());
        assertEquals("INR", info.currency());
        assertTrue(info.active());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(String.class), eq(HttpMethod.GET), request.capture(), eq(String.class));
        assertEquals("test-key", request.getValue().getHeaders().getFirst("X-Api-Key"));
    }

    @Test
    void findSymbol_encodesSymbolsWithSeparators() {
        when(restTemplate.exchange(eq("https://fauxnance.test/symbols/FX%3AEURUSD"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"data\": {\"symbol\": \"FX:EURUSD\", \"name\": \"Euro / US Dollar\", \"type\": \"fx\", \"exchange\": \"FX\", \"currency\": \"USD\", \"active\": true}}"));

        assertEquals("FX", client.findSymbol("FX:EURUSD").orElseThrow().exchange());
    }

    @Test
    void findSymbol_unknownSymbol_isEmpty() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertEquals(Optional.empty(), client.findSymbol("NOPE"));
    }

    @Test
    void findSymbol_inactiveEntry_isReportedInactive() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"data\": {\"symbol\": \"OLD\", \"name\": \"Old Co\", \"type\": \"equity\", \"exchange\": \"NYSE\", \"currency\": \"USD\", \"active\": false}}"));

        assertFalse(client.findSymbol("OLD").orElseThrow().active());
    }

    @Test
    void findSymbol_networkFailure_throws() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("connect timed out"));

        assertThrows(IllegalStateException.class, () -> client.findSymbol("SHOP"));
    }
}
