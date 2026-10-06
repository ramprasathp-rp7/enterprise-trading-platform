package com.tradingplatform.config;

/**
 * Where MarketDataCache gets its quotes from.
 *
 * Set with market-data.quote-source (env MARKET_DATA_QUOTE_SOURCE):
 * - FAUXNANCE (default): MarketDataRefreshScheduler polls the Fauxnance quotes API every five minutes
 * - KAFKA: MarketDataConsumer writes each QUOTE event from the market-data topic
 *
 * Only the writer for the selected source is created, so the cache never has two writers.
 */
public enum QuoteSource {
    FAUXNANCE,  // Scheduled pull from the Fauxnance quotes API
    KAFKA       // Push from the market-data Kafka topic
}
