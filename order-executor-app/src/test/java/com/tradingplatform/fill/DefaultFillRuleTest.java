package com.tradingplatform.fill;

import com.tradingplatform.domain.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultFillRuleTest {
    DefaultFillRule rule = new DefaultFillRule();

    @Test
    void buyLimitAtOrAboveQuoteFills() {
        Order o = new Order("1","INFY.NS","BUY",10,new BigDecimal("100.00"));
        assertEquals(Decision.FILL, rule.decide(o, Optional.of(new BigDecimal("99.99"))));
    }

    @Test
    void sellLimitAtOrBelowQuoteFills() {
        Order o = new Order("1","INFY.NS","SELL",10,new BigDecimal("100.00"));
        assertEquals(Decision.FILL, rule.decide(o, Optional.of(new BigDecimal("100.00"))));
    }

    @Test
    void noPriceReturnsNoPrice() {
        Order o = new Order("1","INFY.NS","BUY",10,new BigDecimal("100.00"));
        assertEquals(Decision.NO_PRICE, rule.decide(o, Optional.empty()));
    }
}
