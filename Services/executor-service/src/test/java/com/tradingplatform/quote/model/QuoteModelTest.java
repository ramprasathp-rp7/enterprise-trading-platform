package com.tradingplatform.quote.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class QuoteModelTest {

    @Test
    void gettersReturnWhatSettersStored() {
        Quote quote = new Quote();
        QuoteWrapper wrapper = new QuoteWrapper();
        wrapper.setSymbol("AAPL");
        wrapper.setSource("fauxnance");
        wrapper.setStale(Boolean.TRUE);
        wrapper.setQuote(quote);

        Data data = new Data();
        data.setSymbol("AAPL");
        data.setInterval("1m");
        data.setCurrency("USD");
        data.setQuotes(List.of(wrapper));

        Object meta = new Object();
        FauxnanceResponse response = new FauxnanceResponse();
        response.setData(data);
        response.setMeta(meta);

        assertEquals("AAPL", wrapper.getSymbol());
        assertEquals("fauxnance", wrapper.getSource());
        assertTrue(wrapper.getStale());
        assertSame(quote, wrapper.getQuote());
        assertEquals("AAPL", data.getSymbol());
        assertEquals("1m", data.getInterval());
        assertEquals("USD", data.getCurrency());
        assertEquals(List.of(wrapper), data.getQuotes());
        assertSame(data, response.getData());
        assertSame(meta, response.getMeta());
    }

    @Test
    void responseDeserialisesAndIgnoresUnknownFields() throws Exception {
        String json = """
            {"data":{"symbol":"AAPL","interval":"1m","currency":"USD","extra":1,
              "quotes":[{"symbol":"AAPL","source":"fx","stale":false,"unknown":"x",
                "quote":{"symbol":"AAPL","bid":189.5,"ask":190.1}}]},
             "meta":{"requestId":"r1"}}
            """;

        FauxnanceResponse response = new ObjectMapper().readValue(json, FauxnanceResponse.class);

        QuoteWrapper wrapper = response.getData().getQuotes().get(0);
        assertEquals("fx", wrapper.getSource());
        assertEquals(new BigDecimal("190.1"), wrapper.getQuote().getAsk());
        assertEquals(new BigDecimal("189.5"), wrapper.getQuote().getBid());
    }
}
