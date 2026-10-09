package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Fauxnance API client with intelligent batching.
 * Batches symbol quote requests (up to 25 symbols per call) for quota efficiency.
 * Tracks quota usage and handles failures gracefully.
 */
@Service
public class FauxnanceApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(FauxnanceApiClient.class);
    private static final int MAX_BATCH_SIZE = 25;
    private static final String QUOTES_ENDPOINT = "/quotes";
    private static final String SYMBOLS_ENDPOINT = "/symbols";
    private static final String USAGE_ENDPOINT = "/usage";
    private static final String HEALTH_ENDPOINT = "/health";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private volatile int quotaRemaining = 2000;
    private volatile Instant lastQuotaCheck = Instant.now();

    public FauxnanceApiClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                              @Value("${FAUXNANCE_API_KEY:}") String apiKey,
                              @Value("${FAUXNANCE_BASE_URL:${fauxnance.url}}") String baseUrl) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey cannot be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        if (apiKey.isBlank()) {
            log.warn("FAUXNANCE_API_KEY is not set; Fauxnance requests will be rejected until it is");
        }
    }

    /**
     * Fetch quotes for multiple symbols in batches.
     * Symbols are batched into groups of 25 (max per API call).
     * 
     * @param symbols Non-empty list of symbols to quote
     * @return Map of symbol to QuoteData. Missing entries indicate symbols that could not be priced.
     */
    public Map<String, QuoteData> getBatchedQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        log.info("getBatchedQuotes called with {} symbols", symbols.size());
        Map<String, QuoteData> result = new HashMap<>();
        
        // Batch symbols into groups of MAX_BATCH_SIZE
        for (int i = 0; i < symbols.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, symbols.size());
            List<String> batch = symbols.subList(i, end);
            
            try {
                log.info("Fetching batch {}/{}: symbols from index {} to {}", 
                    (i / MAX_BATCH_SIZE) + 1, (int) Math.ceil((double) symbols.size() / MAX_BATCH_SIZE), i, end);
                Map<String, QuoteData> batchResult = fetchQuoteBatch(batch);
                log.info("Batch returned {} quotes", batchResult.size());
                result.putAll(batchResult);
            } catch (Exception ex) {
                log.warn("Failed to fetch quotes for batch [{}]: {}", batch, ex.getMessage());
                // Continue with next batch; missing symbols are treated as unpriceable
            }
        }
        
        log.info("getBatchedQuotes returning {} total quotes", result.size());
        return result;
    }

    /**
     * Fetch quotes for a single batch of symbols (up to 25).
     * 
     * @param batch Batch of symbols (max 25)
     * @return Map of symbol to QuoteData
     */
    private Map<String, QuoteData> fetchQuoteBatch(List<String> batch) {
        String symbols = String.join(",", batch);
        String url = String.format("%s%s?symbols=%s", baseUrl, QUOTES_ENDPOINT, symbols);
        
        try {
            log.info("Fetching quotes from Fauxnance: URL={}, symbols={}", url, symbols);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            log.info("Fauxnance response status: {}", response.getStatusCode());
            log.debug("Fauxnance response body: {}", response.getBody());
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Fauxnance API returned non-2xx status: {}", response.getStatusCode());
                throw new RuntimeException("Fauxnance API returned status: " + response.getStatusCode());
            }
            
            return parseQuoteResponse(response.getBody());
        } catch (RestClientException ex) {
            log.error("Fauxnance API call failed for symbols {}: {}", symbols, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Looks a symbol up in the Fauxnance registry (GET /symbols/{symbol}), which also runs an exact
     * upstream discovery for symbols it has not seen before. Costs one request of the daily quota.
     *
     * @return the registry entry, or empty when Fauxnance does not recognise the symbol (400 or 404)
     * @throws IllegalStateException when Fauxnance cannot be reached or answers anything else
     */
    public java.util.Optional<SymbolInfo> findSymbol(String symbol) {
        String url = String.format("%s%s/%s", baseUrl, SYMBOLS_ENDPOINT,
            java.net.URLEncoder.encode(symbol, java.nio.charset.StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", apiKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound
                 | org.springframework.web.client.HttpClientErrorException.BadRequest ex) {
            log.info("Fauxnance does not recognise symbol {}", symbol);
            return java.util.Optional.empty();
        } catch (RestClientException ex) {
            throw new IllegalStateException("Symbol lookup failed: " + ex.getMessage(), ex);
        }
        if (response.getStatusCode().value() == 404 || response.getStatusCode().value() == 400) {
            return java.util.Optional.empty();
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Symbol lookup returned status " + response.getStatusCode());
        }
        try {
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            String name = optionalText(data, "name");
            if (data.isMissingNode() || name == null) {
                return java.util.Optional.empty();
            }
            String returned = optionalText(data, "symbol");
            return java.util.Optional.of(new SymbolInfo(
                returned != null ? returned.toUpperCase(java.util.Locale.ROOT) : symbol,
                name,
                optionalText(data, "type"),
                optionalText(data, "exchange"),
                optionalText(data, "currency"),
                !data.has("active") || data.path("active").asBoolean(true)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the symbol lookup response", ex);
        }
    }

    /**
     * A Fauxnance symbol registry entry.
     *
     * @param type     equity, etf, fx or crypto
     * @param exchange NYSE, NASDAQ, NSE, BSE, FX or CRYPTO
     * @param active   false for a delisted symbol
     */
    public record SymbolInfo(String symbol, String name, String type, String exchange, String currency, boolean active) {
    }

    /**
     * Parse Fauxnance API response into QuoteData objects.
     * Actual response structure from Fauxnance API:
     * {
     *   "data": {
     *     "quotes": [
     *       {
     *         "symbol": "INFY.NS",
     *         "source": "cache",
     *         "stale": true,
     *         "quote": {
     *           "symbol": "INFY.NS",
     *           "price": 1020.5,
     *           "bid": 1020.28,
     *           "ask": 1020.72,
     *           "currency": "INR",
     *           "change": -4.25,
     *           "changePercent": -0.41,
     *           "previousClose": 1024.75,
     *           "asOf": "2026-10-05T09:45:00Z",
     *           "marketState": "unknown"
     *         }
     *       }
     *     ]
     *   },
     *   "meta": {
     *     "asOf": "2026-10-05T14:38:00.631022Z",
     *     "disclaimer": "Educational data. Not for investment use.",
     *     "spreadSource": "modelled"
     *   }
     * }
     */
    private Map<String, QuoteData> parseQuoteResponse(String response) {
        Map<String, QuoteData> result = new HashMap<>();
        
        try {
            log.debug("Raw Fauxnance response: {}", response);
            JsonNode root = objectMapper.readTree(response);
            
            // Navigate to data.quotes array
            JsonNode dataNode = root.get("data");
            if (dataNode == null) {
                log.warn("No 'data' object found in response");
                return result;
            }
            
            JsonNode quotesArray = dataNode.get("quotes");
            if (quotesArray == null || !quotesArray.isArray()) {
                log.warn("No 'quotes' array found in data object");
                return result;
            }
            
            log.debug("Parsing {} quotes from response", quotesArray.size());
            quotesArray.forEach(quoteWrapper -> {
                try {
                    String symbol = quoteWrapper.path("symbol").asText();
                    boolean stale = quoteWrapper.path("stale").asBoolean(false);
                    
                    JsonNode quote = quoteWrapper.get("quote");
                    if (quote == null) {
                        log.warn("Quote wrapper missing 'quote' object for symbol {}", symbol);
                        return;
                    }
                    
                    BigDecimal price = new BigDecimal(quote.path("price").asText());
                    BigDecimal bid = new BigDecimal(quote.path("bid").asText());
                    BigDecimal ask = new BigDecimal(quote.path("ask").asText());
                    // The contract allows a null currency; asText() would turn that into the string "null"
                    String currency = optionalText(quote, "currency");
                    Instant quoteAsOf = Instant.parse(quote.path("asOf").asText());
                    
                    // Optional fields: absent or null for some instruments
                    BigDecimal change = optionalDecimal(quote, "change");
                    BigDecimal changePercent = optionalDecimal(quote, "changePercent");
                    BigDecimal previousClose = optionalDecimal(quote, "previousClose");
                    String marketState = optionalText(quote, "marketState");
                    QuoteData data = new QuoteData(symbol, price, bid, ask, currency, quoteAsOf, stale,
                        change, changePercent, previousClose, marketState);
                    result.put(symbol, data);
                    log.debug("Parsed quote: symbol={}, price={}, bid={}, ask={}, stale={}", 
                        symbol, price, bid, ask, stale);
                } catch (Exception e) {
                    log.error("Failed to parse individual quote: {}", quoteWrapper, e);
                }
            });
            log.info("Successfully parsed {} quotes", result.size());
            
        } catch (Exception ex) {
            log.error("Failed to parse Fauxnance response: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to parse quote response", ex);
        }
        
        return result;
    }

    /** Decimal field value, or null when the field is absent, null or not numeric. */
    private static BigDecimal optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            log.debug("Ignoring non-numeric {} value: {}", field, value);
            return null;
        }
    }

    /** Text field value, or null when the field is absent, null or blank. */
    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    /**
     * Last known remaining daily quota. A field read: it makes no Fauxnance call and costs no quota,
     * which is why the public /health endpoint uses it. Only checkHealth() and getQuotaStatus() refresh
     * it, via GET /usage, which costs one quota unit per call; until then it holds the default.
     */
    public int getQuotaRemaining() {
        return quotaRemaining;
    }

    /**
     * Get the timestamp of the last quota check.
     */
    public Instant getLastQuotaCheck() {
        return lastQuotaCheck;
    }

    /**
     * Check Fauxnance API health and update quota from /usage endpoint.
     * Call this before assuming a quota exhaustion error is permanent.
     * 
     * @return true if API is healthy, false otherwise
     */
    public boolean checkHealth() {
        try {
            String url = String.format("%s%s", baseUrl, HEALTH_ENDPOINT);
            log.debug("Checking Fauxnance health at: {}", url);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Fauxnance health check passed");
                updateQuotaFromUsage();
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn("Fauxnance health check failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Fetch quota status from /usage endpoint.
     * Use this to verify quota availability before assuming a 429 is permanent.
     * 
     * @return Quota remaining for this API key
     */
    public int getQuotaStatus() {
        try {
            String url = String.format("%s%s", baseUrl, USAGE_ENDPOINT);
            log.debug("Fetching quota status from: {}", url);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("quotaRemaining")) {
                int quota = root.get("quotaRemaining").asInt();
                this.quotaRemaining = quota;
                this.lastQuotaCheck = Instant.now();
                log.debug("Quota status: {} remaining", quota);
                return quota;
            }
            return quotaRemaining;
        } catch (Exception ex) {
            log.warn("Failed to fetch quota status: {}", ex.getMessage());
            return quotaRemaining;
        }
    }

    /**
     * Update quota information from /usage endpoint.
     */
    private void updateQuotaFromUsage() {
        try {
            String url = String.format("%s%s", baseUrl, USAGE_ENDPOINT);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("quotaRemaining")) {
                this.quotaRemaining = root.get("quotaRemaining").asInt();
                this.lastQuotaCheck = Instant.now();
            }
        } catch (Exception ex) {
            log.debug("Could not update quota from /usage: {}", ex.getMessage());
        }
    }

    /**
     * Quote data returned by Fauxnance API.
     */
    public static final class QuoteData {
        private final String symbol;
        private final BigDecimal price;
        private final BigDecimal bid;
        private final BigDecimal ask;
        private final String currency;
        private final Instant quoteAsOf;
        private final boolean stale;
        private final BigDecimal change;
        private final BigDecimal changePercent;
        private final BigDecimal previousClose;
        private final String marketState;

        public QuoteData(String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask,
                        String currency, Instant quoteAsOf, boolean stale) {
            this(symbol, price, bid, ask, currency, quoteAsOf, stale, null, null, null, null);
        }

        /**
         * @param change Absolute change against previous close (nullable)
         * @param changePercent Percentage points change (nullable)
         * @param previousClose Previous closing price (nullable)
         * @param marketState Market state: open, closed, pre, post, unknown (nullable)
         */
        public QuoteData(String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask,
                        String currency, Instant quoteAsOf, boolean stale,
                        BigDecimal change, BigDecimal changePercent, BigDecimal previousClose,
                        String marketState) {
            this.symbol = Objects.requireNonNull(symbol);
            this.price = Objects.requireNonNull(price);
            this.bid = Objects.requireNonNull(bid);
            this.ask = Objects.requireNonNull(ask);
            this.currency = currency;  // nullable: Fauxnance may not know a symbol's currency
            this.quoteAsOf = Objects.requireNonNull(quoteAsOf);
            this.stale = stale;
            this.change = change;
            this.changePercent = changePercent;
            this.previousClose = previousClose;
            this.marketState = marketState;
        }

        public String getSymbol() {
            return symbol;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public BigDecimal getBid() {
            return bid;
        }

        public BigDecimal getAsk() {
            return ask;
        }

        public String getCurrency() {
            return currency;
        }

        public Instant getQuoteAsOf() {
            return quoteAsOf;
        }

        public boolean isStale() {
            return stale;
        }

        public BigDecimal getChange() {
            return change;
        }

        public BigDecimal getChangePercent() {
            return changePercent;
        }

        public BigDecimal getPreviousClose() {
            return previousClose;
        }

        /** The cache's QUOTE payload for this quote (same fields the executor's poller publishes). */
        public com.tradingplatform.api.events.QuotePayload toPayload() {
            return new com.tradingplatform.api.events.QuotePayload(symbol, price, bid, ask, currency, change,
                changePercent, previousClose, marketState, stale, quoteAsOf);
        }

        public String getMarketState() {
            return marketState;
        }

        @Override
        public String toString() {
            return "QuoteData{" +
                    "symbol='" + symbol + '\'' +
                    ", price=" + price +
                    ", currency='" + currency + '\'' +
                    ", stale=" + stale +
                    '}';
        }
    }
}
