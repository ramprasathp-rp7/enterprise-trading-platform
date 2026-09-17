package com.tradingplatform.quote;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.quote.model.FauxnanceResponse;

public class FauxnanceQuoteClient implements QuoteClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri;
    private final String apiKey;
    private final int maxAttempts;
    private final long backoffMillis;
    private final long budgetMillis;

    public FauxnanceQuoteClient(String baseUrl, int maxAttempts, long backoffMillis, long budgetMillis) {
        this(baseUrl, null, maxAttempts, backoffMillis, budgetMillis);
    }

    public FauxnanceQuoteClient(String baseUrl, String apiKey, int maxAttempts, long backoffMillis, long budgetMillis) {
        this.baseUri = URI.create(baseUrl);
        this.apiKey = apiKey;
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
        this.budgetMillis = budgetMillis;
    }

    @Override
    public Optional<com.tradingplatform.quote.model.Quote> getLatestPrice(String symbol) throws PricingUnavailableException {
        long start = System.currentTimeMillis();
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
                String base = baseUri.toString();
                if (!base.endsWith("/")) {
                    base += "/";
                }
                URI uri = URI.create(base + "quotes?symbols=" + encodedSymbol);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(5));
                if (apiKey != null && !apiKey.isBlank()) {
                    requestBuilder.header("x-api-key", apiKey);
                }
                HttpRequest req = requestBuilder.GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    FauxnanceResponse fr = mapper.readValue(resp.body(), FauxnanceResponse.class);
                    if (fr.getData() == null) return Optional.empty();
                    var quotes = fr.getData().getQuotes();
                    if (quotes == null || quotes.isEmpty()) return Optional.empty();
                    // take first matching quote wrapper and return its inner quote if present
                    for (int i = 0; i < quotes.size(); i++) {
                        var qw = quotes.get(i);
                        if (qw != null && qw.getQuote() != null) return Optional.of(qw.getQuote());
                    }
                    return Optional.empty();
                } else if (resp.statusCode() == 404) {
                    return Optional.empty();
                } else {
                    if (System.currentTimeMillis() - start > budgetMillis || attempts >= maxAttempts) {
                        throw new PricingUnavailableException("Fauxnance status: " + resp.statusCode());
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (System.currentTimeMillis() - start > budgetMillis || attempts >= maxAttempts) {
                    throw new PricingUnavailableException("request failed", e);
                }
            }

            try { TimeUnit.MILLISECONDS.sleep(backoffMillis); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new PricingUnavailableException("interrupted", ex); }
        }
    }
}
