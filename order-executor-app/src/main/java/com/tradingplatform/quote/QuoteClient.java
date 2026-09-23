package com.tradingplatform.quote;

import java.util.List;
import java.util.Optional;

import com.tradingplatform.quote.model.Quote;

public interface QuoteClient {
    /**
     * Fetch latest price for a single symbol.
     */
    Optional<Quote> getLatestPrice(String symbol) throws PricingUnavailableException;

    /**
     * Fetch latest prices for multiple symbols in batched requests.
     * Internally splits into batches of up to 25 symbols per request.
     * 
     * @param symbols List of symbols to fetch (may be empty)
     * @return List of quotes (may be empty, excludes null/missing quotes)
     * @throws PricingUnavailableException if batch fetching fails after retries
     */
    List<Quote> getLatestPrices(List<String> symbols) throws PricingUnavailableException;

    /**
     * Get the number of Fauxnance API requests made today (auto-resets at UTC midnight).
     * Used for quota monitoring.
     */
    int getTodayRequestCount();

    /**
     * Get remaining quota budget for today (2000 - requests made).
     */
    int getRemainingQuotaBudget();
}
