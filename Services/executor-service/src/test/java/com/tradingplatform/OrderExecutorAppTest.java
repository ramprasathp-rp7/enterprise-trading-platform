package com.tradingplatform;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.tradingplatform.quote.FauxnanceQuoteClient;

class OrderExecutorAppTest {

    @Test
    void quoteClientBeanUsesConfiguredUrlAndKey() {
        OrderExecutorApp app = new OrderExecutorApp();
        assertInstanceOf(FauxnanceQuoteClient.class, app.quoteClient("http://localhost:1/v1", "secret"));
    }
}
