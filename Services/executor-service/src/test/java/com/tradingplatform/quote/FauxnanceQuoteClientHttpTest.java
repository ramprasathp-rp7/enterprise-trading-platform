package com.tradingplatform.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.tradingplatform.quote.model.Quote;

/**
 * Exercises FauxnanceQuoteClient against an in-process HTTP server so the request building,
 * response parsing, retry and batching paths run without touching the real Fauxnance API.
 */
class FauxnanceQuoteClientHttpTest {

    private record StubResponse(int status, String body) {}

    private HttpServer server;
    private String baseUrl;
    private final Deque<StubResponse> responses = new ArrayDeque<>();
    private final List<String> queries = Collections.synchronizedList(new ArrayList<>());
    private final List<String> apiKeys = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/quotes", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            apiKeys.add(exchange.getRequestHeaders().getFirst("x-api-key"));
            StubResponse r;
            synchronized (responses) {
                r = responses.isEmpty() ? new StubResponse(404, "") : responses.poll();
            }
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        synchronized (responses) {
            responses.add(new StubResponse(status, body));
        }
    }

    private static String quotesBody(String... symbols) {
        String wrappers = java.util.Arrays.stream(symbols)
            .map(s -> "{\"symbol\":\"" + s + "\",\"quote\":{\"symbol\":\"" + s + "\",\"bid\":10.10,\"ask\":10.20}}")
            .collect(Collectors.joining(","));
        return "{\"data\":{\"quotes\":[" + wrappers + "]}}";
    }

    private FauxnanceQuoteClient client(int maxAttempts) {
        return new FauxnanceQuoteClient(baseUrl, "secret", maxAttempts, 1L, 5_000L);
    }

    // ---- getLatestPrice ----

    @Test
    void returnsFirstQuoteAndSendsApiKey() throws Exception {
        respond(200, "{\"data\":{\"quotes\":[null,{\"symbol\":\"AAPL\"},"
            + "{\"symbol\":\"AAPL\",\"quote\":{\"symbol\":\"AAPL\",\"bid\":189.5,\"ask\":190.1}}]}}");
        FauxnanceQuoteClient client = client(1);

        Optional<Quote> quote = client.getLatestPrice("AAPL");

        assertTrue(quote.isPresent());
        assertEquals(new BigDecimal("190.1"), quote.get().getAsk());
        assertEquals("symbols=AAPL", queries.get(0));
        assertEquals("secret", apiKeys.get(0));
        assertEquals(1, client.getTodayRequestCount());
        assertEquals(1999, client.getRemainingQuotaBudget());
    }

    @Test
    void encodesSymbolAndOmitsApiKeyWhenNotConfigured() throws Exception {
        respond(200, quotesBody("EURUSD=X"));
        FauxnanceQuoteClient client = new FauxnanceQuoteClient(baseUrl + "/", 1, 1L, 5_000L);

        assertTrue(client.getLatestPrice("EURUSD=X").isPresent());
        assertEquals("symbols=EURUSD%3DX", queries.get(0));
        assertNull(apiKeys.get(0));
    }

    @Test
    void blankApiKeyIsNotSent() throws Exception {
        respond(200, quotesBody("AAPL"));
        new FauxnanceQuoteClient(baseUrl, "  ", 1, 1L, 5_000L).getLatestPrice("AAPL");
        assertNull(apiKeys.get(0));
    }

    @Test
    void emptyOrMissingDataYieldsEmpty() throws Exception {
        respond(200, "{\"meta\":{}}");
        respond(200, "{\"data\":{}}");
        respond(200, "{\"data\":{\"quotes\":[]}}");
        respond(200, "{\"data\":{\"quotes\":[{\"symbol\":\"AAPL\"}]}}");
        FauxnanceQuoteClient client = client(1);

        for (int i = 0; i < 4; i++) {
            assertTrue(client.getLatestPrice("AAPL").isEmpty(), "response " + i);
        }
    }

    @Test
    void notFoundYieldsEmptyWithoutRetry() throws Exception {
        respond(404, "");
        FauxnanceQuoteClient client = client(3);

        assertTrue(client.getLatestPrice("NOPE").isEmpty());
        assertEquals(1, queries.size());
    }

    @Test
    void serverErrorIsRetriedUntilSuccess() throws Exception {
        respond(503, "busy");
        respond(200, quotesBody("AAPL"));

        assertTrue(client(3).getLatestPrice("AAPL").isPresent());
        assertEquals(2, queries.size());
    }

    @Test
    void serverErrorBeyondAttemptsThrows() {
        respond(500, "err");
        respond(500, "err");

        PricingUnavailableException ex = assertThrows(PricingUnavailableException.class,
            () -> client(2).getLatestPrice("AAPL"));
        assertEquals("Fauxnance status: 500", ex.getMessage());
        assertEquals(2, queries.size());
    }

    @Test
    void connectionFailureThrowsAfterAttempts() {
        server.stop(0);

        PricingUnavailableException ex = assertThrows(PricingUnavailableException.class,
            () -> client(2).getLatestPrice("AAPL"));
        assertEquals("request failed", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void invalidJsonBodyIsTreatedAsFailedRequest() {
        respond(200, "not json");

        assertThrows(PricingUnavailableException.class, () -> client(1).getLatestPrice("AAPL"));
    }

    // ---- getLatestPrices ----

    @Test
    void moreThan25SymbolsAreSplitIntoBatches() throws Exception {
        List<String> symbols = IntStream.range(0, 30).mapToObj(i -> "S" + i).toList();
        respond(200, quotesBody(symbols.subList(0, 25).toArray(String[]::new)));
        respond(200, quotesBody(symbols.subList(25, 30).toArray(String[]::new)));
        FauxnanceQuoteClient client = client(1);

        List<Quote> quotes = client.getLatestPrices(symbols);

        assertEquals(30, quotes.size());
        assertEquals(2, queries.size());
        assertEquals(25, queries.get(0).split("%2C|,").length);
        assertEquals("symbols=S25,S26,S27,S28,S29", queries.get(1));
        assertEquals(2, client.getTodayRequestCount());
    }

    @Test
    void batchSkipsNullQuotesAndMissingData() throws Exception {
        respond(200, "{\"data\":{\"quotes\":[null,{\"symbol\":\"A\"},"
            + "{\"symbol\":\"B\",\"quote\":{\"symbol\":\"B\",\"bid\":1,\"ask\":2}}]}}");
        respond(200, "{\"data\":null}");
        List<String> symbols = IntStream.range(0, 26).mapToObj(i -> "S" + i).toList();

        List<Quote> quotes = client(1).getLatestPrices(symbols);

        assertEquals(1, quotes.size());
        assertEquals("B", quotes.get(0).getSymbol());
    }

    @Test
    void batchNotFoundMovesOnToNextBatch() throws Exception {
        respond(404, "");
        respond(200, quotesBody("S25"));
        List<String> symbols = IntStream.range(0, 26).mapToObj(i -> "S" + i).toList();

        List<Quote> quotes = client(3).getLatestPrices(symbols);

        assertEquals(1, quotes.size());
        assertEquals(2, queries.size());
    }

    @Test
    void batchServerErrorIsRetriedThenSucceeds() throws Exception {
        respond(502, "");
        respond(200, quotesBody("AAPL", "MSFT"));

        assertEquals(2, client(2).getLatestPrices(List.of("AAPL", "MSFT")).size());
        assertEquals(2, queries.size());
    }

    @Test
    void batchServerErrorBeyondAttemptsThrows() {
        respond(500, "");

        PricingUnavailableException ex = assertThrows(PricingUnavailableException.class,
            () -> client(1).getLatestPrices(List.of("AAPL")));
        assertEquals("Fauxnance status: 500", ex.getMessage());
    }

    @Test
    void batchConnectionFailureThrows() {
        server.stop(0);
        FauxnanceQuoteClient client = new FauxnanceQuoteClient(baseUrl + "/", null, 2, 1L, 5_000L);

        PricingUnavailableException ex = assertThrows(PricingUnavailableException.class,
            () -> client.getLatestPrices(List.of("AAPL")));
        assertEquals("request failed", ex.getMessage());
    }
}
