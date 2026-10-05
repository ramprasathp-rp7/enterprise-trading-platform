package com.tradingplatform;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.tradingplatform.quote.FauxnanceQuoteClient;

class OrderExecutorAppTest {

    @Test
    void quoteClientBeanUsesDefaultsWhenUnconfigured() {
        OrderExecutorApp app = new OrderExecutorApp(new MockEnvironment());
        assertInstanceOf(FauxnanceQuoteClient.class, app.quoteClient());
    }

    @Test
    void quoteClientBeanUsesConfiguredUrlAndKey() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("FAUXNANCE_BASE_URL", "http://localhost:1/v1")
            .withProperty("FAUXNANCE_API_KEY", "secret");
        assertInstanceOf(FauxnanceQuoteClient.class, new OrderExecutorApp(env).quoteClient());
    }
}
