package com.tradingplatform.kafka;

import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.OrderPlacedPayload;
import com.tradingplatform.executor.OrderExecutorService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {
    private final OrderExecutorService executorService;

    public OrderPlacedListener(OrderExecutorService executorService) {
        this.executorService = executorService;
    }

    @KafkaListener(topics = "orders", groupId = "trade-executor-group")
    public void onOrderPlaced(MessageEnvelope<OrderPlacedPayload> envelope) {
        var p = envelope.payload();
        // Build local Order model
        var order = new com.tradingplatform.domain.Order(
            p.orderId(), p.symbol(), p.side(), p.quantity(), p.price()
        );
        executorService.process(p.orderId(), p.accountId(), order);
    }
}
