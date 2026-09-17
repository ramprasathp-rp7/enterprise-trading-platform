package com.tradingplatform.fill;

import com.tradingplatform.domain.model.Order;
import java.math.BigDecimal;
import java.util.Optional;

public interface FillRule {
    Decision decide(Order order, Optional<BigDecimal> quotedPrice);
}
