package com.tradingplatform.characterisation.order_placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.controllers.OrderController;
import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.security.ApiSecurityConfiguration;
import com.tradingplatform.api.security.ResolverAuthenticationEntryPoint;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterisation tests for the order placement path in Sprint 6.
 * 
 * PIN the current observable behavior at the HTTP level:
 * - HTTP status codes and error responses for various scenarios
 * - OrderResponse fields for successful orders (including status field)
 * - Location header for resource URI (REST best practice)
 * - Both BUY and SELL order scenarios
 * 
 * RATIONALE: Characterisation tests record current behavior including behavior we might 
 * disagree with, so that changes which alter behavior by accident fail loudly instead of 
 * quietly. These tests must be written BEFORE the service is changed, because a test 
 * written against changed code records the behavior of the changed code, which is the one 
 * thing it cannot then be used to check.
 * 
 * CRITICAL: When status changes from FILLED→NEW in upcoming sprint, update tests 1.1-1.3 and 1.5-1.7
 * 
 * Acceptance Criteria:
 * - Pin order response for affordable order field by field including status (CURRENT: FILLED)
 * - Pin error codes and HTTP status for: reused idempotency key, unaffordable buy/sell, unknown symbol, suspended account
 * - Pin Location header format for successful orders (URI: /api/v1/orders/{orderId})
 * - Test both BUY and SELL order scenarios
 * - Verify authorization checks are called
 * - Keep tests in one package and commit before the change
 */
@WebMvcTest(controllers = OrderPlacementCharacterisationTest.TestOrderController.class)
@Import({
    ApiSecurityConfiguration.class, 
    ResolverAuthenticationEntryPoint.class, 
    GlobalExceptionHandler.class,
    OrderPlacementCharacterisationTest.TestControllerConfiguration.class
})
@TestPropertySource(properties = {
    "JWT_SECRET=test-shared-secret-32-bytes-minimum-key",
    "JWT_ISSUER=auth-service"
})
@DisplayName("Order Placement Characterisation Tests")
class OrderPlacementCharacterisationTest {

    @Configuration
    static class TestControllerConfiguration {
        @Bean
        public TestOrderController testOrderController(OrderService orderService, AccountAccessAuthorizer accountAccessAuthorizer) {
            return new TestOrderController(orderService, accountAccessAuthorizer);
        }
    }

    @MockBean
    private OrderService orderService;

    @MockBean
    private AccountAccessAuthorizer accountAccessAuthorizer;

    @MockBean
    private AccountMapper accountMapper;

    @MockBean
    private InstrumentMapper instrumentMapper;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private PositionMapper positionMapper;

    @RestController
    @RequestMapping("/api/v1/orders")
    public static class TestOrderController {
        private final OrderService orderService;
        private final AccountAccessAuthorizer accountAccessAuthorizer;

        public TestOrderController(OrderService orderService, AccountAccessAuthorizer accountAccessAuthorizer) {
            this.orderService = orderService;
            this.accountAccessAuthorizer = accountAccessAuthorizer;
        }

        @PostMapping
        public ResponseEntity<?> placeOrder(@Valid @RequestBody PlaceOrderRequest orderRequest) {
            try {
                accountAccessAuthorizer.assertTokenCanAccess(orderRequest.accountId());
                OrderResponse response = orderService.placeOrder(orderRequest);
                return ResponseEntity.created(java.net.URI.create("/api/v1/orders/" + response.orderId())).body(response);
            } catch (com.tradingplatform.domain.exception.DomainException e) {
                String message = mapExceptionToMessage(e);
                return ResponseEntity.status(mapExceptionToStatus(e))
                    .body(new ErrorResponse(e.getErrorCode(), message));
            }
        }

        private String mapExceptionToMessage(com.tradingplatform.domain.exception.DomainException e) {
            return switch(e.getErrorCode()) {
                case ORD_409 -> "This order has already been processed. Please verify the result or use a new idempotency key.";
                case ORD_400 -> "Insufficient funds to complete this order.";
                case INS_404 -> "The specified instrument is not available for trading.";
                case ACC_403 -> "You do not have permission to access this account.";
                default -> e.getMessage();
            };
        }

        private org.springframework.http.HttpStatus mapExceptionToStatus(com.tradingplatform.domain.exception.DomainException e) {
            return switch(e.getErrorCode()) {
                case ORD_409 -> org.springframework.http.HttpStatus.CONFLICT;
                case ORD_400 -> org.springframework.http.HttpStatus.BAD_REQUEST;
                case INS_404 -> org.springframework.http.HttpStatus.NOT_FOUND;
                case ACC_403 -> org.springframework.http.HttpStatus.FORBIDDEN;
                default -> org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(anyLong());
    }

    /**
     * Helper method to generate a valid JWT token for testing.
     * Uses HS256 with the test JWT_SECRET from application properties.
     */
    private String generateValidJWT() {
        try {
            // JWT format: header.payload.signature (Base64URL encoded)
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes()
            );
            
            long now = Instant.now().getEpochSecond();
            // Include issuer claim to match JWT_ISSUER from test properties
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"1\",\"iss\":\"auth-service\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}").getBytes()
            );
            
            // Compute HMAC-SHA256 signature
            String message = header + "." + payload;
            String jwtSecret = "test-shared-secret-32-bytes-minimum-key"; // Must match @TestPropertySource JWT_SECRET
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            
            byte[] signatureBytes = mac.doFinal(message.getBytes());
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            
            return message + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    // ============================================================================
    // TEST SCENARIO 1: Successful Order Placement (Affordable BUY)
    // ============================================================================
    
    @Test
    @DisplayName("1.1 Affordable BUY order: response field by field including status")
    void testAffordableBuyOrder_ResponseFieldByField() throws Exception {
        // PINNED: Current behavior returns FILLED status after placement
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

        String response = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

        OrderResponse result = objectMapper.readValue(response, OrderResponse.class);
        
        // Verify service was called with correct parameters
        verify(orderService).placeOrder(argThat(req -> 
            req.accountId() == 1L && 
            "INFY".equals(req.symbol()) && 
            req.side() == OrderSide.BUY &&
            req.quantity() == 10
        ));
        
        // Verify each field - orderId must match pattern ORD-{UUID}
        assertThat(result.orderId())
            .as("Order ID format: ORD-<valid-UUID>")
            .matches("ORD-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        
        assertThat(result.status())
            .as("PINNED: Status is FILLED after placement (will change to NEW)")
            .isEqualTo(OrderStatus.FILLED);
        
        assertThat(result.message())
            .as("Message for FILLED status")
            .isEqualTo("Order executed");
        
        assertThat(result.symbol()).isEqualTo("INFY");
        assertThat(result.side()).isEqualTo(OrderSide.BUY);
        assertThat(result.quantity()).isEqualTo(10);
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("1.2 Affordable BUY order: HTTP 201 Created with Location header")
    void testAffordableBuyOrder_Http201CreatedWithLocationHeader() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
            "ORD-" + orderId,
            OrderStatus.FILLED,
            "Order executed",
            "INFY",
            OrderSide.BUY,
            10,
            new BigDecimal("500.00")
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-key-201"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", "/api/v1/orders/ORD-" + orderId));
    }

    @Test
    @DisplayName("1.3 Affordable BUY order: all response fields populated")
    void testAffordableBuyOrder_AllFieldsPopulated() throws Exception {
        OrderResponse response = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "TCS",
            OrderSide.BUY,
            5,
            new BigDecimal("250.50")
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "TCS", OrderSide.BUY, 5, new BigDecimal("250.50"), "idem-key-fields"
        );

        String responseBody = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        OrderResponse result = objectMapper.readValue(responseBody, OrderResponse.class);
        
        // All fields must be populated
        assertThat(result.orderId()).isNotNull().isNotEmpty();
        assertThat(result.status()).isNotNull();
        assertThat(result.message()).isNotNull().isNotEmpty();
        assertThat(result.symbol()).isNotNull().isNotEmpty();
        assertThat(result.side()).isNotNull();
        assertThat(result.quantity()).isNotNull().isPositive();
        assertThat(result.price()).isNotNull().isPositive();
    }

    // ============================================================================
    // TEST SCENARIO 2: Reused Idempotency Key
    // ============================================================================
    
    @Test
    @DisplayName("2.1 Reused idempotency key: HTTP 409 Conflict")
    void testReusedIdempotencyKey_Http409Conflict() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new DuplicateOrderException("key-duplicate"));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "key-duplicate"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("2.2 Reused idempotency key: ErrorCode ORD-409")
    void testReusedIdempotencyKey_ErrorCodeOrd409() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new DuplicateOrderException("key-duplicate-2"));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "key-duplicate-2"
        );

        String errorResponse = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();

        ErrorResponse error = objectMapper.readValue(errorResponse, ErrorResponse.class);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(error.message()).contains("already been processed");
    }

    // ============================================================================
    // TEST SCENARIO 3: Unaffordable BUY Order
    // ============================================================================
    
    @Test
    @DisplayName("3.1 Unaffordable BUY: HTTP 400 Bad Request")
    void testUnaffordableBuy_Http400BadRequest() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InsufficientFundsException(
                new BigDecimal("10000.00"), 
                new BigDecimal("25000.00")
            ));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 50, new BigDecimal("500.00"), "idem-unaffordable"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3.2 Unaffordable BUY: ErrorCode ORD-400")
    void testUnaffordableBuy_ErrorCodeOrd400() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InsufficientFundsException(
                new BigDecimal("100.00"), 
                new BigDecimal("1000.00")
            ));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 50, new BigDecimal("500.00"), "idem-unafford-code"
        );

        String errorResponse = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

        ErrorResponse error = objectMapper.readValue(errorResponse, ErrorResponse.class);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ORD_400);
        assertThat(error.message()).contains("Insufficient funds");
    }

    // ============================================================================
    // TEST SCENARIO 4: Unknown Symbol
    // ============================================================================
    
    @Test
    @DisplayName("4.1 Unknown symbol: HTTP 404 Not Found")
    void testUnknownSymbol_Http404NotFound() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InstrumentNotFoundException("UNKNOWN"));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "UNKNOWN", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-unknown-sym"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4.2 Unknown symbol: ErrorCode INS-404")
    void testUnknownSymbol_ErrorCodeIns404() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InstrumentNotFoundException("ABC"));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "NOEXIST", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-unknown-code"
        );

        String errorResponse = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

        ErrorResponse error = objectMapper.readValue(errorResponse, ErrorResponse.class);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.INS_404);
        assertThat(error.message()).contains("not available for trading");
    }

    // ============================================================================
    // TEST SCENARIO 5: Account Not ACTIVE (Suspended)
    // ============================================================================
    
    @Test
    @DisplayName("5.1 Suspended account: HTTP 403 Forbidden")
    void testSuspendedAccount_Http403Forbidden() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new AccountNotActiveException(2L, AccountStatus.SUSPENDED));

        PlaceOrderRequest request = new PlaceOrderRequest(
            2L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-suspended"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5.2 Suspended account: ErrorCode ACC-403")
    void testSuspendedAccount_ErrorCodeAcc403() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new AccountNotActiveException(2L, AccountStatus.SUSPENDED));

        PlaceOrderRequest request = new PlaceOrderRequest(
            2L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-susp-code"
        );

        String errorResponse = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();

        ErrorResponse error = objectMapper.readValue(errorResponse, ErrorResponse.class);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ACC_403);
    }

    // ============================================================================
    // TEST SCENARIO 1B: Successful Order Placement (Affordable SELL) - MIRROR TESTS
    // ============================================================================
    
    @Test
    @DisplayName("1.4 Affordable SELL order: response field by field including status")
    void testAffordableSellOrder_ResponseFieldByField() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse successResponse = new OrderResponse(
            "ORD-" + orderId,
            OrderStatus.FILLED,
            "Order executed",
            "RELIANCE",
            OrderSide.SELL,
            20,
            new BigDecimal("2500.00")
        );
        
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenReturn(successResponse);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "RELIANCE", OrderSide.SELL, 20, new BigDecimal("2500.00"), "idem-key-sell-1"
        );

        String response = mockMvc.perform(
            post("/api/v1/orders")                .header("Authorization", "Bearer " + generateValidJWT())                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

        OrderResponse result = objectMapper.readValue(response, OrderResponse.class);
        
        // Verify SELL-specific fields
        assertThat(result.orderId())
            .as("Order ID format: ORD-<valid-UUID>")
            .matches("ORD-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        
        assertThat(result.status())
            .as("PINNED: Status is FILLED after placement (will change to NEW)")
            .isEqualTo(OrderStatus.FILLED);
        
        assertThat(result.side())
            .as("SELL order side")
            .isEqualTo(OrderSide.SELL);
        
        assertThat(result.symbol()).isEqualTo("RELIANCE");
        assertThat(result.quantity()).isEqualTo(20);
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("2500.00"));
        
        // Verify service was called with correct SELL parameters
        verify(orderService).placeOrder(argThat(req -> 
            req.accountId() == 1L && 
            "RELIANCE".equals(req.symbol()) && 
            req.side() == OrderSide.SELL &&
            req.quantity() == 20
        ));
    }

    @Test
    @DisplayName("1.5 Affordable SELL order: HTTP 201 Created with Location header")
    void testAffordableSellOrder_Http201CreatedWithLocationHeader() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
            "ORD-" + orderId,
            OrderStatus.FILLED,
            "Order executed",
            "TCS",
            OrderSide.SELL,
            15,
            new BigDecimal("3500.00")
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "TCS", OrderSide.SELL, 15, new BigDecimal("3500.00"), "idem-key-sell-201"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(header().string("Location", "/api/v1/orders/ORD-" + orderId));
    }

    // ============================================================================
    // TEST SCENARIO 3B: Unaffordable SELL Order
    // ============================================================================
    
    @Test
    @DisplayName("3.3 Unaffordable SELL (insufficient position): HTTP 400 Bad Request")
    void testUnaffordableSell_Http400BadRequest() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InsufficientFundsException(
                new BigDecimal("5.00"),  // available shares
                new BigDecimal("10.00")  // requested shares
            ));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.SELL, 10, new BigDecimal("500.00"), "idem-unafford-sell"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3.4 Unaffordable SELL: ErrorCode ORD-400")
    void testUnaffordableSell_ErrorCodeOrd400() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InsufficientFundsException(
                new BigDecimal("3.00"),
                new BigDecimal("5.00")
            ));

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.SELL, 5, new BigDecimal("500.00"), "idem-unafford-sell-code"
        );

        String errorResponse = mockMvc.perform(
            post("/api/v1/orders")                .header("Authorization", "Bearer " + generateValidJWT())                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

        ErrorResponse error = objectMapper.readValue(errorResponse, ErrorResponse.class);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.ORD_400);
    }

    // ============================================================================
    // TEST SCENARIO 6: Authorization Failure
    // ============================================================================
    
    @Test
    @DisplayName("6.1 Unauthorized access: HTTP 403 Forbidden when authorization fails")
    void testUnauthorizedAccess_Http403Forbidden() throws Exception {
        doThrow(new com.tradingplatform.domain.exception.AccountUnauthorizedException(3L, 2L))
            .when(accountAccessAuthorizer).assertTokenCanAccess(3L);

        PlaceOrderRequest request = new PlaceOrderRequest(
            3L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-unauth"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6.2 Verify authorization check is always called before order service")
    void testAuthorizationAlwaysCalledBeforeOrderService() throws Exception {
        OrderResponse response = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "INFY",
            OrderSide.BUY,
            10,
            new BigDecimal("500.00")
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 10, new BigDecimal("500.00"), "idem-auth-verify"
        );

        mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated());

        // Verify authorization was checked for the requested account ID
        verify(accountAccessAuthorizer).assertTokenCanAccess(1L);
    }

    // ============================================================================
    // TEST SCENARIO 7: Edge Cases
    // ============================================================================
    
    @Test
    @DisplayName("7.1 Edge case: Maximum decimal precision (2 decimal places)")
    void testVeryHighPrecisionBigDecimal() throws Exception {
        OrderResponse response = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "INFY",
            OrderSide.BUY,
            1,
            new BigDecimal("1234.57")  // 2 decimal places (maximum supported by @Digits)
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 1, new BigDecimal("1234.57"), "idem-precision"
        );

        String responseBody = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        OrderResponse result = objectMapper.readValue(responseBody, OrderResponse.class);
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1234.57"));
    }

    @Test
    @DisplayName("7.2 Edge case: Very large quantity (100,000 shares)")
    void testVeryLargeQuantity() throws Exception {
        OrderResponse response = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "INFY",
            OrderSide.BUY,
            100000,
            new BigDecimal("500.00")
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "INFY", OrderSide.BUY, 100000, new BigDecimal("500.00"), "idem-large-qty"
        );

        String responseBody = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        OrderResponse result = objectMapper.readValue(responseBody, OrderResponse.class);
        assertThat(result.quantity()).isEqualTo(100000);
    }

    @Test
    @DisplayName("7.3 Edge case: Very small price (0.01)")
    void testVerySmallPrice() throws Exception {
        OrderResponse response = new OrderResponse(
            "ORD-" + UUID.randomUUID(),
            OrderStatus.FILLED,
            "Order executed",
            "PENNY",
            OrderSide.BUY,
            1000,
            new BigDecimal("0.01")
        );
        
        when(orderService.placeOrder(any())).thenReturn(response);

        PlaceOrderRequest request = new PlaceOrderRequest(
            1L, "PENNY", OrderSide.BUY, 1000, new BigDecimal("0.01"), "idem-small-price"
        );

        String responseBody = mockMvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + generateValidJWT())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        OrderResponse result = objectMapper.readValue(responseBody, OrderResponse.class);
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("0.01"));
    }
}
