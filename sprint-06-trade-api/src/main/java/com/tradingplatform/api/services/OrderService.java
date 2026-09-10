package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderValidationService orderValidationService;
    private final OrderSettlementService orderSettlementService;
    private final OrderMapper orderMapper;
    private final InstrumentMapper instrumentMapper;
    
    public OrderService(OrderValidationService orderValidationService, 
                        OrderSettlementService orderSettlementService,
                        OrderMapper orderMapper,
                        InstrumentMapper instrumentMapper) {
        this.orderValidationService = orderValidationService;
        this.orderSettlementService = orderSettlementService;
        this.orderMapper = orderMapper;
        this.instrumentMapper = instrumentMapper;
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest orderRequest) {
        // Validate and create order (throws DomainException on validation failure)
        Order newOrder = orderValidationService.validateAndCreateOrder(orderRequest);

        // Persist order to database via OrderMapper
        orderMapper.insertOrder(
            newOrder.getId(),
            newOrder.getAccountId(),
            newOrder.getInstrumentId(),
            newOrder.getSide(),
            newOrder.getQuantity(),
            newOrder.getPrice(),
            OrderStatus.NEW,  // Order starts in NEW status
            newOrder.getIdempotencyKey()
        );

        // Settle the order: move cash, update position, mark FILLED
        orderSettlementService.settle(newOrder, newOrder.getPrice());

        // Fetch the settled order from database (now with FILLED status and executedPrice)
        Order settledOrder = orderMapper.findOrderById(newOrder.getId());
        if (settledOrder == null) {
            throw new IllegalStateException("Order not found after settlement: " + newOrder.getId());
        }

        // Fetch instrument for order response
        Instrument instrument = instrumentMapper.findById(settledOrder.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("Instrument not found for order: " + settledOrder.getInstrumentId());
        }

        // Build and return OrderResponse from entities
        return buildOrderResponse(settledOrder, instrument);
    }

    public OrderResponse getOrderById(String orderId) {
        UUID id = UUID.fromString(orderId);

        // Fetch order from database via OrderMapper
        Order order = orderMapper.findOrderById(id);
        if (order == null) {
            throw new NoSuchElementException("Order not found: " + orderId);
        }

        // Fetch instrument for order response
        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("Instrument not found for order: " + order.getInstrumentId());
        }

        // Build and return OrderResponse from entities
        return buildOrderResponse(order, instrument);
    }


    public OrderResponse cancelOrderById(String orderId) {
        UUID id = UUID.fromString(orderId);
        int rowsUpdated = orderMapper.cancelOrderIfNew(id);

        if (rowsUpdated == 0) {
            throw new IllegalStateException("Cannot cancel order: either not found or not in NEW status");
        }

        // Fetch the cancelled order from database
        Order cancelledOrder = orderMapper.findOrderById(id);
        if (cancelledOrder == null) {
            throw new NoSuchElementException("Order not found after cancellation: " + orderId);
        }

        // Fetch instrument for order response
        Instrument instrument = instrumentMapper.findById(cancelledOrder.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("Instrument not found for order: " + cancelledOrder.getInstrumentId());
        }

        // Build and return OrderResponse from entities
        return buildOrderResponse(cancelledOrder, instrument);
    }

    private OrderResponse buildOrderResponse(Order order, Instrument instrument) {
        // Determine message based on order status
        String message = switch(order.getStatus()) {
            case NEW -> "Order accepted";
            case FILLED -> "Order executed";
            case REJECTED -> "Order rejected";
            case CANCELLED -> "Order cancelled";
            default -> "Unknown status";
        };

        // Build and return OrderResponse
        return new OrderResponse(
            "ORD-" + order.getId().toString(),
            order.getStatus(),
            message,
            instrument.getSymbol(),
            order.getSide(),
            order.getQuantity(),
            order.getPrice()
        );
    }
}
