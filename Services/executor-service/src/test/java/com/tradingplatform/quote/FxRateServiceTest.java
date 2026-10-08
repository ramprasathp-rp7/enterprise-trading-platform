package com.tradingplatform.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.quote.model.Quote;

class FxRateServiceTest {

    private QuoteClient quoteClient;
    private FxRateService service;

    @BeforeEach
    void setUp() {
        quoteClient = mock(QuoteClient.class);
        service = new FxRateService(quoteClient);
    }

    private static Quote quote(String symbol, String price) {
        Quote q = new Quote();
        q.setSymbol(symbol);
        q.setPrice(new BigDecimal(price));
        return q;
    }

    @Test
    void sameCurrencyIsOneWithoutAQuote() throws Exception {
        assertEquals(BigDecimal.ONE, service.rate("usd", "USD"));
        verifyNoInteractions(quoteClient);
    }

    @Test
    void directPairUsesItsPrice() throws Exception {
        when(quoteClient.getLatestPrices(List.of("FX:EURUSD", "FX:USDEUR")))
            .thenReturn(List.of(quote("FX:EURUSD", "1.0850")));

        assertEquals(new BigDecimal("1.0850000000"), service.rate("EUR", "USD"));
    }

    @Test
    void inversePairUsesOneOverItsPrice() throws Exception {
        when(quoteClient.getLatestPrices(List.of("FX:INRUSD", "FX:USDINR")))
            .thenReturn(List.of(quote("FX:USDINR", "80")));

        assertEquals(new BigDecimal("0.0125000000"), service.rate("INR", "USD"));
    }

    @Test
    void anyCurrencyFauxnanceQuotesWorks() throws Exception {
        when(quoteClient.getLatestPrices(List.of("FX:CHFUSD", "FX:USDCHF")))
            .thenReturn(List.of(quote("FX:USDCHF", "0.8")));

        assertEquals(new BigDecimal("1.2500000000"), service.rate("CHF", "USD"));
    }

    @Test
    void missingPairIsAPricingOutage() throws Exception {
        when(quoteClient.getLatestPrices(List.of("FX:XYZUSD", "FX:USDXYZ"))).thenReturn(List.of());

        assertThrows(PricingUnavailableException.class, () -> service.rate("XYZ", "USD"));
    }
}
