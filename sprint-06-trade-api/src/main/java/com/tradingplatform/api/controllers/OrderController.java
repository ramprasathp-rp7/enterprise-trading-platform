package com.tradingplatform.api.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;

import org.apache.ibatis.annotations.Delete;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;



@RestController 
@RequestMapping("api/v1/orders")
public class OrderController {
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest orderRequest) {
        //TODO: process POST request
        
        return new ResponseEntity();
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String param) {
        return new ResponseEntity();
    }
    
    
}
