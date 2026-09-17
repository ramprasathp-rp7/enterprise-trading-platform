package com.tradingplatform.service;

import com.tradingplatform.domain.Order;
import java.math.BigDecimal;

public interface OrderSettlementService {
    void settle(Order order, BigDecimal executedPrice);
}
