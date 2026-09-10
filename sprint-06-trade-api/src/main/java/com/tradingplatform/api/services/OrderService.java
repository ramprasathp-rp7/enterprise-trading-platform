package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderStatus;
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
    
    public OrderService(OrderValidationService orderValidationService, 
                        OrderSettlementService orderSettlementService,
                        OrderMapper orderMapper) {
        this.orderValidationService = orderValidationService;
        this.orderSettlementService = orderSettlementService;
        this.orderMapper = orderMapper;
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

        // Fetch the settled order from database and return as response
        OrderResponse response = orderMapper.findOrderResponseById(newOrder.getId());
        if (response == null) {
            throw new IllegalStateException("Order not found after settlement: " + newOrder.getId());
        }
        return response;
    }

    public OrderResponse getOrderById(String orderId) {

        UUID id = UUID.fromString(orderId);

        // Fetch order from database via OrderMapper
        OrderResponse order = orderMapper.findOrderResponseById(id);
        if (order == null) {
            throw new NoSuchElementException("Order not found: " + orderId);
        }
        return order;
    }


    public OrderResponse cancelOrderById(String orderId) {

        UUID id = UUID.fromString(orderId);
        int rowsUpdated = orderMapper.cancelOrderIfNew(id);

        if (rowsUpdated == 0) {
            throw new IllegalStateException("Cannot cancel order: either not found or not in NEW status");
        }

        // Fetch and return the cancelled order
        OrderResponse cancelledOrder = orderMapper.findCancelledOrder(id);
        if (cancelledOrder == null) {
            throw new NoSuchElementException("Order not found after cancellation: " + orderId);
        }

        return cancelledOrder;
    }

    public double calculateFee(String ticker, double tradeValue) {
        // TODO: Implement fee calculation logic
        // Example: 0.1% fee for standard instruments
        // double feePercentage = getFeePercentage(ticker);
        // return tradeValue * (feePercentage / 100.0);
        
        return 0.0;
    }
}
