package com.tradingplatform.fill;

import java.math.BigDecimal;
import java.util.Optional;

import com.tradingplatform.domain.Order;

public interface FillRule {
    Decision decide(Order order, Optional<BigDecimal> quotedPrice);
}
