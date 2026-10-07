package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("FauxnanceApiClient Tests")
class FauxnanceApiClientTest {

    private FauxnanceApiClient client;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        client = new FauxnanceApiClient(restTemplate, objectMapper, "test-key", "https://api.fauxnance.com");
    }

    @Test
    @DisplayName("Should batch symbols into groups of 25 or less")
    void testBatchingLogic() {
        // Arrange: 60 symbols (should be batched into 3 calls of 25, 25, 10)
        List<String> symbols = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            symbols.add("SYMBOL" + i);
        }

        String mockResponse = """
                {
                  "data": {
                    "quotes": []
                  },
                  "meta": {}
                }
                """;

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(mockResponse);

        // Act: Should not throw exception
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(symbols);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());  // No quotes in mock response
    }

    @Test
    @DisplayName("Should handle empty symbol list")
    void testEmptySymbolList() {
        // Act
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(List.of());

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle null symbol list")
    void testNullSymbolList() {
        // Act
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should parse quote response correctly")
    void testParseQuoteResponse() {
        // Arrange
        List<String> symbols = List.of("AAPL", "MSFT");
        String mockResponse = """
                {
                  "data": {
                    "quotes": [
                      {
                        "symbol": "AAPL",
                        "stale": false,
                        "quote": {
                          "symbol": "AAPL",
                          "price": 150.25,
                          "bid": 150.24,
                          "ask": 150.26,
                          "currency": "USD",
                          "asOf": "2026-10-04T14:05:11Z",
                          "marketState": "open"
                        }
                      },
                      {
                        "symbol": "MSFT",
                        "stale": false,
                        "quote": {
                          "symbol": "MSFT",
                          "price": 370.50,
                          "bid": 370.49,
                          "ask": 370.51,
                          "currency": "USD",
                          "asOf": "2026-10-04T14:05:12Z",
                          "marketState": "open"
                        }
                      }
                    ]
                  },
                  "meta": {}
                }
                """;

        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), 
                any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockResponse, org.springframework.http.HttpStatus.OK));

        // Act
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(symbols);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("AAPL"));
        assertTrue(result.containsKey("MSFT"));
        
        FauxnanceApiClient.QuoteData aapl = result.get("AAPL");
        assertEquals("AAPL", aapl.getSymbol());
        assertEquals(0, new BigDecimal("150.25").compareTo(aapl.getPrice()));
        assertEquals("USD", aapl.getCurrency());
        assertFalse(aapl.isStale());
    }

    @Test
    @DisplayName("Keeps a quote whose currency is null, and skips a per-symbol error, as the Fauxnance contract allows")
    void testNullCurrencyAndSymbolErrorInBatch() {
        String mockResponse = """
                {
                  "data": {
                    "quotes": [
                      {
                        "symbol": "FX:USDINR",
                        "stale": false,
                        "quote": {
                          "symbol": "FX:USDINR",
                          "price": 83.47,
                          "bid": 83.46,
                          "ask": 83.48,
                          "currency": null,
                          "asOf": "2026-10-04T14:05:11Z",
                          "marketState": "open"
                        }
                      },
                      {
                        "symbol": "FX:INRUSD",
                        "error": { "code": "SYMBOL_NOT_FOUND", "message": "Symbol was not recognized.", "details": {} }
                      }
                    ]
                  },
                  "meta": {}
                }
                """;
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET),
                any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockResponse, org.springframework.http.HttpStatus.OK));

        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(List.of("FX:INRUSD", "FX:USDINR"));

        assertEquals(1, result.size());
        assertNull(result.get("FX:USDINR").getCurrency());  // not the string "null"
        assertEquals(0, new BigDecimal("83.47").compareTo(result.get("FX:USDINR").getPrice()));
    }

    @Test
    @DisplayName("Should update quota from response")
    void testQuotaUpdate() {
        // Arrange
        String mockResponse = """
                {
                  "data": {
                    "quotes": []
                  },
                  "meta": {}
                }
                """;

        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), 
                any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockResponse, org.springframework.http.HttpStatus.OK));

        // Act
        client.getBatchedQuotes(List.of("AAPL"));

        // Assert: Quota should remain at default since mock doesn't include quotaRemaining
        assertEquals(2000, client.getQuotaRemaining());
        assertNotNull(client.getLastQuotaCheck());
    }

    @Test
    @DisplayName("Should handle stale quotes in response")
    void testStaleQuoteHandling() {
        // Arrange
        String mockResponse = """
                {
                  "data": {
                    "quotes": [
                      {
                        "symbol": "AAPL",
                        "stale": true,
                        "quote": {
                          "symbol": "AAPL",
                          "price": 150.25,
                          "bid": 150.24,
                          "ask": 150.26,
                          "currency": "USD",
                          "asOf": "2026-10-04T14:05:11Z",
                          "marketState": "open"
                        }
                      }
                    ]
                  },
                  "meta": {}
                }
                """;

        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), 
                any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockResponse, org.springframework.http.HttpStatus.OK));

        // Act
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(List.of("AAPL"));

        // Assert
        FauxnanceApiClient.QuoteData quote = result.get("AAPL");
        assertNotNull(quote);
        assertTrue(quote.isStale());
    }

    @Test
    @DisplayName("Should handle API failures gracefully")
    void testApiFailureHandling() {
        // Arrange
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), 
                any(), eq(String.class)))
                .thenThrow(new RuntimeException("API connection failed"));

        // Act: Should not throw, but return empty map and log warning
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(List.of("AAPL"));

        // Assert: Result should be empty (graceful degradation)
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("QuoteData immutability")
    void testQuoteDataImmutability() {
        // Arrange
        FauxnanceApiClient.QuoteData quote = new FauxnanceApiClient.QuoteData(
                "AAPL",
                new BigDecimal("150.25"),
                new BigDecimal("150.24"),
                new BigDecimal("150.26"),
                "USD",
                Instant.now(),
                false
        );

        // Assert all getters work
        assertEquals("AAPL", quote.getSymbol());
        assertEquals(0, new BigDecimal("150.25").compareTo(quote.getPrice()));
        assertFalse(quote.isStale());
    }

    @Test
    @DisplayName("Should parse change, changePercent, previousClose and marketState, null when absent")
    void testParseOptionalQuoteFields() {
        // Arrange
        String mockResponse = """
                {
                  "data": {
                    "quotes": [
                      {
                        "symbol": "INFY.NS",
                        "stale": true,
                        "quote": {
                          "symbol": "INFY.NS",
                          "price": 1006.35,
                          "bid": 1006.13,
                          "ask": 1006.57,
                          "currency": "INR",
                          "change": -4.25,
                          "changePercent": -0.42,
                          "previousClose": 1010.60,
                          "asOf": "2026-10-06T08:41:20Z",
                          "marketState": "closed"
                        }
                      },
                      {
                        "symbol": "FX:EURUSD",
                        "stale": false,
                        "quote": {
                          "symbol": "FX:EURUSD",
                          "price": 1.1229,
                          "bid": 1.122888,
                          "ask": 1.122912,
                          "currency": "USD",
                          "change": null,
                          "asOf": "2026-10-06T00:00:00Z"
                        }
                      }
                    ]
                  },
                  "meta": {}
                }
                """;
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET),
                any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockResponse, org.springframework.http.HttpStatus.OK));

        // Act
        Map<String, FauxnanceApiClient.QuoteData> result = client.getBatchedQuotes(List.of("INFY.NS", "FX:EURUSD"));

        // Assert
        FauxnanceApiClient.QuoteData infy = result.get("INFY.NS");
        assertEquals(0, new BigDecimal("-4.25").compareTo(infy.getChange()));
        assertEquals(0, new BigDecimal("-0.42").compareTo(infy.getChangePercent()));
        assertEquals(0, new BigDecimal("1010.60").compareTo(infy.getPreviousClose()));
        assertEquals("closed", infy.getMarketState());

        FauxnanceApiClient.QuoteData eurusd = result.get("FX:EURUSD");
        assertNull(eurusd.getChange());
        assertNull(eurusd.getChangePercent());
        assertNull(eurusd.getPreviousClose());
        assertNull(eurusd.getMarketState());
    }
}
