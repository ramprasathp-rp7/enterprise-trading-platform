package com.tradingplatform.api.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.api.services.OrderService;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;

import java.net.URI;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



@RestController 
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest orderRequest) {
    
        // Fixneeded
        double tradeValue = orderRequest.quantity() * orderRequest.price().doubleValue();
        double fee = orderService.calculateFee(orderRequest.symbol(), tradeValue);

        OrderResponse response = orderService.placeOrder(orderRequest);
        URI location = URI.create("/api/v1/orders/" + response.orderId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {

        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }
    
    @PatchMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String id) {

        OrderResponse order = orderService.cancelOrderById(id);
        return ResponseEntity.ok(order);
    }
    
    
}
