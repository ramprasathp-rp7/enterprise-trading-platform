package com.tradingplatform.quote;

import java.util.Optional;

import com.tradingplatform.quote.model.Quote;

public interface QuoteClient {
    Optional<Quote> getLatestPrice(String symbol) throws PricingUnavailableException;
}
