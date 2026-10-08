package com.tradingplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Validates market-data.quote-source at startup and logs the active quote source.
 *
 * The writers switch on the raw property, so a typo would leave the cache with no writer;
 * binding it to QuoteSource here makes an unknown value fail startup instead.
 */
@Configuration
public class QuoteSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(QuoteSourceConfig.class);

    private final QuoteSource quoteSource;

    public QuoteSourceConfig(@Value("${market-data.quote-source:FAUXNANCE}") QuoteSource quoteSource) {
        this.quoteSource = quoteSource;
        logger.info("Market data quote source: {}", quoteSource == QuoteSource.KAFKA
            ? "KAFKA (market-data topic)"
            : "FAUXNANCE (quotes API, scheduled refresh)");
    }

    public QuoteSource getQuoteSource() {
        return quoteSource;
    }
}
