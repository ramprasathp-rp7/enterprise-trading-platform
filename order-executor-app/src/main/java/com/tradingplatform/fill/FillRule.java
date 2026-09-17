package com.tradingplatform.fill;

import java.util.Optional;

import com.tradingplatform.domain.Order;
import com.tradingplatform.quote.model.Quote;

public interface FillRule {
    Decision decide(Order order, Optional<Quote> quoted);
}
