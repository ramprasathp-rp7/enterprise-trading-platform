package com.tradingplatform.api.controllers;

import com.tradingplatform.api.security.AccountAccessAuthorizer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.services.OrderHistoryService;
import com.tradingplatform.api.services.OrderService;
import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



@RestController 
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderHistoryService orderHistoryService;
    private final OrderService orderService;
    private final AccountAccessAuthorizer accountAccessAuthorizer;

    public OrderController(OrderHistoryService orderHistoryService, OrderService orderService, AccountAccessAuthorizer accountAccessAuthorizer) {
        this.orderHistoryService = orderHistoryService;
        this.orderService = orderService;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest orderRequest) {
        accountAccessAuthorizer.assertTokenCanAccess(orderRequest.accountId());

        OrderResponse response = orderService.placeOrder(orderRequest);
        String rawUuid = response.orderId().substring(4);  // Strip "ORD-" prefix
        URI location = URI.create("/api/v1/orders/" + rawUuid);
        return ResponseEntity.created(location).body(response);
        }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String id) {

        OrderResponse order = orderService.cancelOrderById(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderStatusChange>> getOrderStatusHistory(@PathVariable String id) {
        UUID orderId = UUID.fromString(id);
        long accountId = orderHistoryService.getOrderAccountId(orderId);
        accountAccessAuthorizer.assertTokenCanAccess(accountId);
        List<OrderStatusChange> history = orderHistoryService.getOrderHistory(orderId);
        return ResponseEntity.ok(history);
    }
    
    
}
