package com.tradingplatform.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.security.JwtAuthenticationConverter;
import com.tradingplatform.api.services.OrderHistoryService;
import com.tradingplatform.api.services.TransactionService;
import com.tradingplatform.api.services.FeeService;
import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Admin Controller Authorization Tests
 * 
 * Verifies that:
 * 1. Admin tokens (roles: ["ADMIN"]) CAN access /api/v1/admin/** endpoints → 200
 * 2. Customer tokens (roles: ["CUSTOMER"]) CANNOT access /api/v1/admin/** → 403
 * 3. No token CANNOT access /api/v1/admin/** → 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "JWT_SECRET=test-shared-secret-32-bytes-minimum-key",
    "JWT_ISSUER=auth-service"
})
class AdminControllerTest {

    private static final String SECRET = "test-shared-secret-32-bytes-minimum-key";
    private static final long ACCOUNT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderHistoryService orderHistoryService;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private FeeService feeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mock empty responses for admin endpoints
        when(orderHistoryService.getAllOrderHistory()).thenReturn(List.of());
        when(transactionService.getAllTransactions()).thenReturn(List.of());
    }

    /**
     * Test: Admin token with roles: ["ADMIN"] can access /api/v1/admin/orders/history
     * Expected: 200 OK
     */
    @Test
    void adminToken_canAccessOrderHistory_returns200() throws Exception {
        String adminToken = createToken("ADMIN", ACCOUNT_ID);

        mockMvc.perform(
                get("/api/v1/admin/orders/history")
                        .header("Authorization", "Bearer " + adminToken)
        )
                .andExpect(status().isOk());
    }

    /**
     * Test: Customer token with roles: ["CUSTOMER"] cannot access /api/v1/admin/orders/history
     * Expected: 403 Forbidden
     */
    @Test
    void customerToken_cannotAccessOrderHistory_returns403() throws Exception {
        String customerToken = createToken("CUSTOMER", ACCOUNT_ID);

        mockMvc.perform(
                get("/api/v1/admin/orders/history")
                        .header("Authorization", "Bearer " + customerToken)
        )
                .andExpect(status().isForbidden());
    }

    /**
     * Test: No token cannot access /api/v1/admin/orders/history
     * Expected: 401 Unauthorized
     */
    @Test
    void noToken_cannotAccessOrderHistory_returns401() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/orders/history")
        )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test: Admin token with roles: ["ADMIN"] can access /api/v1/admin/transactions
     * Expected: 200 OK
     */
    @Test
    void adminToken_canAccessTransactionHistory_returns200() throws Exception {
        String adminToken = createToken("ADMIN", ACCOUNT_ID);

        mockMvc.perform(
                get("/api/v1/admin/transactions")
                        .header("Authorization", "Bearer " + adminToken)
        )
                .andExpect(status().isOk());
    }

    /**
     * Test: Customer token with roles: ["CUSTOMER"] cannot access /api/v1/admin/transactions
     * Expected: 403 Forbidden
     */
    @Test
    void customerToken_cannotAccessTransactionHistory_returns403() throws Exception {
        String customerToken = createToken("CUSTOMER", ACCOUNT_ID);

        mockMvc.perform(
                get("/api/v1/admin/transactions")
                        .header("Authorization", "Bearer " + customerToken)
        )
                .andExpect(status().isForbidden());
    }

    /**
     * Test: No token cannot access /api/v1/admin/transactions
     * Expected: 401 Unauthorized
     */
    @Test
    void noToken_cannotAccessTransactionHistory_returns401() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/transactions")
        )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Create a signed JWT token with the specified role.
     * 
     * @param role "ADMIN" or "CUSTOMER"
     * @param accountId the trading account ID
     * @return signed JWT token
     */
    private String createToken(String role, long accountId) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + 900; // 15 minutes

        String header = base64Url(
                toJson(Map.of(
                        "alg", "HS256",
                        "typ", "JWT"
                ))
        );

        String payload = base64Url(
                toJson(Map.of(
                        "sub", "test-user-id",
                        "accountId", accountId,
                        "roles", List.of(role),
                        "iat", issuedAt,
                        "exp", expiresAt,
                        "iss", "auth-service"
                ))
        );

        String signature = sign(header + "." + payload);

        return header + "." + payload + "." + signature;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }
}
