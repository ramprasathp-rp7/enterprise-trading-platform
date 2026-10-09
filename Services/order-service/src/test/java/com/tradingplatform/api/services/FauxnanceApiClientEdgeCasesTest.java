package com.tradingplatform.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.services.FauxnanceApiClient.QuoteData;

/**
 * Fauxnance responses the happy-path tests don't reach: errors, odd payloads, health and quota.
 */
class FauxnanceApiClientEdgeCasesTest {

    private static final String BASE = "https://fauxnance.test";

    private final RestTemplate rest = mock(RestTemplate.class);
    private final FauxnanceApiClient client = new FauxnanceApiClient(rest, new ObjectMapper(), "key", BASE);

    private void exchange(String path, ResponseEntity<String> response) {
        when(rest.exchange(contains(path), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(response);
    }

    private void exchangeFails(String path, RuntimeException error) {
        when(rest.exchange(contains(path), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenThrow(error);
    }

    @Test
    void aBlankApiKeyIsAllowedButRequestsCarryIt() {
        assertThat(new FauxnanceApiClient(rest, new ObjectMapper(), "", BASE).getQuotaRemaining()).isEqualTo(2000);
    }

    @Test
    void aFailingBatchLeavesItsSymbolsUnpriced() {
        exchange("/quotes", new ResponseEntity<>("{}", HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(client.getBatchedQuotes(List.of("AAPL"))).isEmpty();
    }

    @Test
    void anUnreachableQuoteServiceLeavesSymbolsUnpriced() {
        exchangeFails("/quotes", new ResourceAccessException("timeout"));
        assertThat(client.getBatchedQuotes(List.of("AAPL"))).isEmpty();
    }

    @Test
    void responsesWithoutDataOrQuotesYieldNothing() {
        exchange("/quotes", ResponseEntity.ok("{\"meta\":{}}"));
        assertThat(client.getBatchedQuotes(List.of("AAPL"))).isEmpty();
    }

    @Test
    void aDataObjectWithoutAQuotesArrayYieldsNothing() {
        exchange("/quotes", ResponseEntity.ok("{\"data\":{\"quotes\":{}}}"));
        assertThat(client.getBatchedQuotes(List.of("AAPL"))).isEmpty();
    }

    @Test
    void anUnreadableBodyIsTreatedAsAFailedBatch() {
        exchange("/quotes", ResponseEntity.ok("not json"));
        assertThat(client.getBatchedQuotes(List.of("AAPL"))).isEmpty();
    }

    @Test
    void badQuotesAreSkippedAndOptionalFieldsTolerateJunk() {
        exchange("/quotes", ResponseEntity.ok("""
            {"data":{"quotes":[
              {"symbol":"NOQ","stale":false},
              {"symbol":"BAD","quote":{"price":"abc","bid":"1","ask":"1","asOf":"2026-10-09T10:00:00Z"}},
              {"symbol":"AAPL","stale":true,"quote":{"price":"336.67","bid":"336.63","ask":"336.71","currency":null,
                "asOf":"2026-10-09T10:00:00Z","change":"n/a","changePercent":null,"previousClose":"333.63","marketState":" "}}
            ]}}"""));

        Map<String, QuoteData> quotes = client.getBatchedQuotes(List.of("NOQ", "BAD", "AAPL"));

        assertThat(quotes).containsOnlyKeys("AAPL");
        QuoteData aapl = quotes.get("AAPL");
        assertThat(aapl.getCurrency()).isNull();
        assertThat(aapl.getChange()).isNull();
        assertThat(aapl.getChangePercent()).isNull();
        assertThat(aapl.getPreviousClose()).isEqualByComparingTo("333.63");
        assertThat(aapl.getMarketState()).isNull();
        assertThat(aapl.isStale()).isTrue();
    }

    @Test
    void quoteDataExposesEveryFieldAndBecomesACachePayload() {
        Instant asOf = Instant.parse("2026-10-09T10:00:00Z");
        QuoteData quote = new QuoteData("MSFT", new BigDecimal("512.40"), new BigDecimal("512.30"), new BigDecimal("512.50"),
            "USD", asOf, false, new BigDecimal("-4.1"), new BigDecimal("-0.79"), new BigDecimal("516.5"), "open");

        assertThat(quote.getSymbol()).isEqualTo("MSFT");
        assertThat(quote.getPrice()).isEqualByComparingTo("512.40");
        assertThat(quote.getBid()).isEqualByComparingTo("512.30");
        assertThat(quote.getAsk()).isEqualByComparingTo("512.50");
        assertThat(quote.getQuoteAsOf()).isEqualTo(asOf);
        assertThat(quote.getChangePercent()).isEqualByComparingTo("-0.79");
        assertThat(quote.toString()).contains("symbol='MSFT'", "currency='USD'", "stale=false");
        QuotePayload payload = quote.toPayload();
        assertThat(payload.symbol()).isEqualTo("MSFT");
        assertThat(payload.marketState()).isEqualTo("open");

        QuoteData simple = new QuoteData("EURUSD", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "USD", asOf, true);
        assertThat(simple.getChange()).isNull();
        assertThat(simple.getMarketState()).isNull();
    }

    @Test
    void symbolLookupTreatsA404OrBadRequestStatusAsUnknown() {
        exchange("/symbols/ZZZ", new ResponseEntity<>(HttpStatus.NOT_FOUND));
        exchange("/symbols/%3F", new ResponseEntity<>(HttpStatus.BAD_REQUEST));

        assertThat(client.findSymbol("ZZZ")).isEmpty();
        assertThat(client.findSymbol("?")).isEmpty();
    }

    @Test
    void symbolLookupFailsOnServerErrorsEmptyBodiesAndJunk() {
        exchange("/symbols/ERR", new ResponseEntity<>("{}", HttpStatus.INTERNAL_SERVER_ERROR));
        exchange("/symbols/EMPTY", ResponseEntity.ok().build());
        exchange("/symbols/JUNK", ResponseEntity.ok("<html>"));

        assertThatThrownBy(() -> client.findSymbol("ERR")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.findSymbol("EMPTY")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.findSymbol("JUNK")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not read");
    }

    @Test
    void symbolLookupWithoutANameIsUnknownAndMissingFieldsFallBack() {
        exchange("/symbols/NONAME", ResponseEntity.ok("{\"data\":{\"symbol\":\"NONAME\"}}"));
        exchange("/symbols/shop", ResponseEntity.ok("{\"data\":{\"name\":\"Shopify Inc.\"}}"));

        assertThat(client.findSymbol("NONAME")).isEmpty();
        FauxnanceApiClient.SymbolInfo shop = client.findSymbol("shop").orElseThrow();
        assertThat(shop.symbol()).isEqualTo("shop");
        assertThat(shop.exchange()).isNull();
        assertThat(shop.active()).isTrue();
    }

    @Test
    void healthCheckRefreshesTheQuota() {
        when(rest.getForEntity(BASE + "/health", String.class)).thenReturn(ResponseEntity.ok("{}"));
        exchange("/usage", ResponseEntity.ok("{\"quotaRemaining\":1234}"));

        assertThat(client.checkHealth()).isTrue();
        assertThat(client.getQuotaRemaining()).isEqualTo(1234);
        assertThat(client.getLastQuotaCheck()).isNotNull();
    }

    @Test
    void healthCheckFailsOnBadStatusOrErrorsAndKeepsTheQuotaWhenUsageIsUnreadable() {
        when(rest.getForEntity(BASE + "/health", String.class))
            .thenReturn(new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE))
            .thenThrow(new ResourceAccessException("down"))
            .thenReturn(ResponseEntity.ok("{}"));
        exchange("/usage", ResponseEntity.ok("not json"));

        assertThat(client.checkHealth()).isFalse();
        assertThat(client.checkHealth()).isFalse();
        assertThat(client.checkHealth()).isTrue();
        assertThat(client.getQuotaRemaining()).isEqualTo(2000);
    }

    @Test
    void quotaStatusReadsUsageAndFallsBackToTheLastKnownValue() {
        when(rest.exchange(contains("/usage"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"quotaRemaining\":1500}"))
            .thenReturn(ResponseEntity.ok("{\"other\":1}"))
            .thenThrow(new ResourceAccessException("down"));

        assertThat(client.getQuotaStatus()).isEqualTo(1500);
        assertThat(client.getQuotaStatus()).isEqualTo(1500);
        assertThat(client.getQuotaStatus()).isEqualTo(1500);
    }
}
