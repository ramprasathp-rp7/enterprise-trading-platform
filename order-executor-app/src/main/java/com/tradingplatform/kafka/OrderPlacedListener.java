package com.tradingplatform.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.tradingplatform.mappers.InstrumentMapper;
import com.tradingplatform.domain.Instrument;
import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.events.MessageEnvelope;
import com.tradingplatform.events.OrderPlacedPayload;
import com.tradingplatform.executor.OrderExecutorService;

@Component
public class OrderPlacedListener {
	private final OrderExecutorService executorService;
	private final InstrumentMapper instrumentMapper;

	public OrderPlacedListener(OrderExecutorService executorService, InstrumentMapper instrumentMapper) {
		this.executorService = executorService;
		this.instrumentMapper = instrumentMapper;
	}

	@KafkaListener(topics = "orders", groupId = "trade-executor-group")
	public void onOrderPlaced(MessageEnvelope<OrderPlacedPayload> envelope) {
		var p = envelope.payload();

		// Resolve instrument by symbol to obtain instrumentId used by domain Order
		Instrument instrument = instrumentMapper.findBySymbol(p.symbol());
		if (instrument == null) {
			// If instrument can't be resolved, reject early by invoking executor with null instrument id
			executorService.process(p.orderId(), p.accountId(), null);
			return;
		}

		OrderSide side = OrderSide.valueOf(p.side().toUpperCase());

		Order order = new Order(
			p.accountId(),
			instrument.getId(),
			side,
			p.quantity(),
			p.price(),
			p.idempotencyKey()
		);

		executorService.process(p.orderId(), p.accountId(), order);
	}
}
