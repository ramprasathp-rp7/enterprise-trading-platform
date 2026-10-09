package com.tradingplatform.instruments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstrumentExchangesTest {

    @ParameterizedTest
    @CsvSource({
        "FX:EURUSD, FX",
        "X:BTC-USD, CRYPTO",
        "INFY.NS, NSE",
        "tatasteel.bo, BSE",
        "PFE, NYSE",
        "JPM, NYSE",
        "AAPL, NASDAQ",
    })
    void derive_followsTheMigrationBackfillRules(String symbol, String expected) {
        assertEquals(expected, InstrumentExchanges.derive(symbol));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "AAPL, NYSE, NYSE",
        "AAPL, nasdaq, NASDAQ",
        "AAPL, NULL, NASDAQ",
        "INFY.NS, LSE, NSE",
    }, nullValues = "NULL")
    void resolve_keepsAKnownStoredExchange_elseDerives(String symbol, String stored, String expected) {
        assertEquals(expected, InstrumentExchanges.resolve(symbol, stored));
    }
}
