package com.tradingplatform.service;

import com.tradingplatform.domain.Order;

public interface OrderRepository {
    void updateStatus(String orderId, String status, String reason);
}
