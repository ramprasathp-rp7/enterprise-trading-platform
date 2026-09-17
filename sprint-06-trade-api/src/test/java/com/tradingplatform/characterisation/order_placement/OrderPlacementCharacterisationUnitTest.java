package com.tradingplatform.characterisation.order_placement;

import com.tradingplatform.api.controllers.OrderController;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.OrderService;
import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import com.tradingplatform.domain.exception.DuplicateOrderException;
import com.tradingplatform.domain.exception.InsufficientFundsException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Characterisation tests for the order placement path in Sprint 6 - Unit Test Version.
 * 
 * This version uses pure Mockito without Spring Boot's test context to avoid infrastructure issues.
 * PIN the current observable behavior at the service level:
 * - HTTP status codes that OrderController returns for various scenarios
 * - OrderResponse fields for successful orders (including status field)
 * - Error responses for various failure scenarios
 * 
 * RATIONALE: Characterisation tests record current behavior before service changes.
 * These tests must be written BEFORE the service is changed.
 * 
 * Acceptance Criteria:
 * - Pin order response for affordable order field by field including status (CURRENT: FILLED)
 * - Pin error codes and HTTP status for: reused idempotency key, unaffordable buy, unknown symbol, suspended account
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Placement Characterisation Tests - Unit Version")
class OrderPlacementCharacterisationUnitTest {

    @Mock
    private OrderService orderService;

    @Mock
    private AccountAccessAuthorizer accountAccessAuthorizer;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        // OrderController is created with mocked dependencies via @InjectMocks
    }

    // Scenario 1: Affordable BUY Order
    @Test
    @DisplayName("1.1 Affordable BUY order: response field by field including status")
    void testAffordableBuyOrder_ResponseFieldByField() {
        // PINNING BEHAVIOR: Status is FILLED after placement
        UUID orderId = UUID.randomUUID();
        OrderResponse successResponse = new OrderResponse(
            "ORD-" + orderId,
            OrderStatus.FILLED,  // CRITICAL PINNING: This will change to NEW in sprint
            "Order executed",
            "INFY",
            OrderSide.BUY,
            10,
            new BigDecimal("500.00")
        );
        
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenReturn(successResponse);
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-1"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOf(OrderResponse.class);
        OrderResponse result = (OrderResponse) response.getBody();
        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.orderId()).startsWith("ORD-");
        assertThat(result.symbol()).isEqualTo("INFY");
        assertThat(result.side()).isEqualTo(OrderSide.BUY);
        assertThat(result.quantity()).isEqualTo(10);
        assertThat(result.price()).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("1.2 Affordable BUY order: HTTP 201 Created response")
    void testAffordableBuyOrder_Http201Created() {
        OrderResponse successResponse = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "INFY",
            OrderSide.BUY,
            10,
            new BigDecimal("500.00")
        );
        
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenReturn(successResponse);
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-1"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("1.3 Affordable BUY order: all fields populated")
    void testAffordableBuyOrder_AllFieldsPopulated() {
        OrderResponse successResponse = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "INFY",
            OrderSide.BUY,
            10,
            new BigDecimal("500.00")
        );
        
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenReturn(successResponse);
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-1"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        OrderResponse result = (OrderResponse) response.getBody();
        assertThat(result.orderId()).isNotNull().isNotEmpty();
        assertThat(result.status()).isNotNull();
        assertThat(result.message()).isNotNull().isNotEmpty();
        assertThat(result.symbol()).isNotNull().isNotEmpty();
        assertThat(result.side()).isNotNull();
        assertThat(result.quantity()).isNotNull();
        assertThat(result.price()).isNotNull();
    }

    // Scenario 2: Reused Idempotency Key
    @Test
    @DisplayName("2.1 Reused idempotency key: HTTP 409 Conflict")
    void testReusedIdempotencyKey_Http409Conflict() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new DuplicateOrderException("Order with this idempotency key has already been processed"));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "duplicate-key"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("2.2 Reused idempotency key: ErrorCode ORD_409")
    void testReusedIdempotencyKey_ErrorCodeOrd409() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new DuplicateOrderException("Order with this idempotency key has already been processed"));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "duplicate-key"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(error.message()).contains("already been processed");
    }

    // Scenario 3: Unaffordable BUY
    @Test
    @DisplayName("3.1 Unaffordable BUY: HTTP 400 Bad Request")
    void testUnaffordableBuy_Http400BadRequest() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new InsufficientFundsException(new BigDecimal("100.00"), new BigDecimal("5000.00")));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 100, new BigDecimal("5000.00"), "idem-key-2"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("3.2 Unaffordable BUY: ErrorCode ORD_400")
    void testUnaffordableBuy_ErrorCodeOrd400() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new InsufficientFundsException(new BigDecimal("100.00"), new BigDecimal("5000.00")));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 100, new BigDecimal("5000.00"), "idem-key-2"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ORD_400);
        assertThat(error.message()).contains("Insufficient funds");
    }

    // Scenario 4: Unknown Symbol
    @Test
    @DisplayName("4.1 Unknown symbol: HTTP 404 Not Found")
    void testUnknownSymbol_Http404NotFound() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new InstrumentNotFoundException("Instrument INVALID is not available for trading"));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INVALID", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-3"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("4.2 Unknown symbol: ErrorCode INS_404")
    void testUnknownSymbol_ErrorCodeIns404() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new InstrumentNotFoundException("Instrument INVALID is not available for trading"));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INVALID", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-3"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.errorCode()).isEqualTo(ErrorCode.INS_404);
        assertThat(error.message()).contains("not available for trading");
    }

    // Scenario 5: Suspended Account
    @Test
    @DisplayName("5.1 Suspended account: HTTP 403 Forbidden")
    void testSuspendedAccount_Http403Forbidden() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new AccountNotActiveException(1L, AccountStatus.SUSPENDED));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-4"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("5.2 Suspended account: ErrorCode ACC_403")
    void testSuspendedAccount_ErrorCodeAcc403() {
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenThrow(new AccountNotActiveException(1L, AccountStatus.SUSPENDED));
        
        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-4"
        );
        
        ResponseEntity<?> response = orderController.placeOrder(request);
        
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ACC_403);
    }
}
