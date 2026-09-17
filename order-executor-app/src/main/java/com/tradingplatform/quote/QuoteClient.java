package com.tradingplatform.quote;

import java.math.BigDecimal;
import java.util.Optional;

public interface QuoteClient {
    Optional<BigDecimal> getLatestPrice(String symbol) throws PricingUnavailableException;
}
