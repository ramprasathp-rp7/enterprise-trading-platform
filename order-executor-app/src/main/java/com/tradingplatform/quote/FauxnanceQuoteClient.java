package com.tradingplatform.quote;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.quote.model.FauxnanceResponse;
import com.tradingplatform.quote.model.Quote;

public class FauxnanceQuoteClient implements QuoteClient {
    private static final int DAILY_QUOTA_LIMIT = 2000;
    private static final int MAX_BATCH_SIZE = 25;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri;
    private final String apiKey;
    private final int maxAttempts;
    private final long backoffMillis;
    private final long budgetMillis;

    // Quota tracking: thread-safe counter that resets at UTC midnight
    private final AtomicInteger requestCountToday = new AtomicInteger(0);
    private volatile LocalDate lastRequestDate = LocalDate.now(ZoneId.of("UTC"));

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
                incrementRequestCount();
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

    @Override
    public List<Quote> getLatestPrices(List<String> symbols) throws PricingUnavailableException {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }

        List<Quote> results = new ArrayList<>();
        
        // Split into batches of up to 25 symbols
        for (int i = 0; i < symbols.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, symbols.size());
            List<String> batch = symbols.subList(i, end);
            
            long start = System.currentTimeMillis();
            int attempts = 0;
            
            while (true) {
                attempts++;
                try {
                    // Build comma-separated symbol list
                    String symbolList = batch.stream()
                        .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
                        .collect(Collectors.joining(","));
                    
                    String base = baseUri.toString();
                    if (!base.endsWith("/")) {
                        base += "/";
                    }
                    URI uri = URI.create(base + "quotes?symbols=" + symbolList);
                    
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(5));
                    if (apiKey != null && !apiKey.isBlank()) {
                        requestBuilder.header("x-api-key", apiKey);
                    }
                    
                    HttpRequest req = requestBuilder.GET().build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    incrementRequestCount();
                    
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        FauxnanceResponse fr = mapper.readValue(resp.body(), FauxnanceResponse.class);
                        if (fr.getData() != null && fr.getData().getQuotes() != null) {
                            var quotes = fr.getData().getQuotes();
                            for (var qw : quotes) {
                                if (qw != null && qw.getQuote() != null) {
                                    results.add(qw.getQuote());
                                }
                            }
                        }
                        break; // Batch succeeded, move to next batch
                    } else if (resp.statusCode() == 404) {
                        break; // No quotes found, move to next batch
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

                try { 
                    TimeUnit.MILLISECONDS.sleep(backoffMillis); 
                } catch (InterruptedException ex) { 
                    Thread.currentThread().interrupt(); 
                    throw new PricingUnavailableException("interrupted", ex); 
                }
            }
        }
        
        return results;
    }

    @Override
    public int getTodayRequestCount() {
        resetCounterIfDateChanged();
        return requestCountToday.get();
    }

    @Override
    public int getRemainingQuotaBudget() {
        resetCounterIfDateChanged();
        return Math.max(0, DAILY_QUOTA_LIMIT - requestCountToday.get());
    }

    /**
     * Increment request counter and check if date has rolled over to UTC midnight.
     */
    private void incrementRequestCount() {
        resetCounterIfDateChanged();
        requestCountToday.incrementAndGet();
    }

    /**
     * Check if the date has changed to UTC midnight and reset counter if so.
     */
    private synchronized void resetCounterIfDateChanged() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        if (!today.equals(lastRequestDate)) {
            lastRequestDate = today;
            requestCountToday.set(0);
        }
    }
}
