package com.tradingplatform.fill;

import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.quote.model.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class DefaultFillRule implements FillRule {
    @Override
    public Decision decide(Order order, Optional<Quote> quoted) {
        if (quoted == null || quoted.isEmpty()) return Decision.NO_PRICE;
        Quote q = quoted.get();
        BigDecimal limit = order.getPrice();

        BigDecimal sidePrice;
        if (order.getSide() == OrderSide.BUY) {
            sidePrice = q.getAsk();
        } else {
            sidePrice = q.getBid();
        }

        if (sidePrice == null) return Decision.NO_PRICE;

        // Round quote side to the same scale as the order's limit price before comparison
        BigDecimal rounded = sidePrice.setScale(limit.scale(), RoundingMode.HALF_UP);

        if (order.getSide() == OrderSide.BUY) {
            if (limit.compareTo(rounded) >= 0) return Decision.FILL;
            return Decision.REJECT;
        } else {
            if (limit.compareTo(rounded) <= 0) return Decision.FILL;
            return Decision.REJECT;
        }
    }
}
