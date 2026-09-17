package com.tradingplatform.fill;

import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.enums.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultFillRuleTest {
    DefaultFillRule rule = new DefaultFillRule();

    @Test
    void buyLimitAtOrAboveQuoteFills() {
        Order o = new Order(1L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1");
        assertEquals(Decision.FILL, rule.decide(o, Optional.of(new BigDecimal("99.99"))));
    }

    @Test
    void sellLimitAtOrBelowQuoteFills() {
        Order o = new Order(1L, 100L, OrderSide.SELL, 10, new BigDecimal("100.00"), "key-1");
        assertEquals(Decision.FILL, rule.decide(o, Optional.of(new BigDecimal("100.00"))));
    }

    @Test
    void noPriceReturnsNoPrice() {
        Order o = new Order(1L, 100L, OrderSide.BUY, 10, new BigDecimal("100.00"), "key-1");
        assertEquals(Decision.NO_PRICE, rule.decide(o, Optional.empty()));
    }
}
