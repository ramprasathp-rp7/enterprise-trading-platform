package com.tradingplatform.fill;

import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
import java.math.BigDecimal;
import java.util.Optional;

public class DefaultFillRule implements FillRule {
    @Override
    public Decision decide(Order order, Optional<BigDecimal> quotedPrice) {
        if (quotedPrice == null || quotedPrice.isEmpty()) return Decision.NO_PRICE;
        BigDecimal q = quotedPrice.get();
        BigDecimal limit = order.getPrice();
        if (order.getSide() == OrderSide.BUY) {
            if (limit.compareTo(q) >= 0) return Decision.FILL;
            return Decision.REJECT;
        } else {
            // SELL
            if (limit.compareTo(q) <= 0) return Decision.FILL;
            return Decision.REJECT;
        }
    }
}
