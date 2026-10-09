package com.tradingplatform.strategy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tradingplatform.domain.enums.OrderSide;

class TradeApiOrderClientTest {

    private static final String KEY = "STRAT-7-1791370800000";

    private MockRestServiceServer server;
    private TradeApiOrderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trade-api");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TradeApiOrderClient(builder.build());
    }

    private TradeApiOrderClient.PlacedOrder place() {
        return client.placeOrder("tok", 101L, "AAPL", OrderSide.BUY, 5, new BigDecimal("150.25"), KEY);
    }

    @Test
    void placesTheOrderThroughPostOrdersWithTheBearerTokenAndIdempotencyKey() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer tok"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accountId").value(101))
            .andExpect(jsonPath("$.symbol").value("AAPL"))
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.quantity").value(5))
            .andExpect(jsonPath("$.price").value(150.25))
            .andExpect(jsonPath("$.idempotencyKey").value(KEY))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"ORD-abc\",\"status\":\"NEW\",\"symbol\":\"AAPL\"}"));

        TradeApiOrderClient.PlacedOrder placed = place();

        assertEquals("ORD-abc", placed.orderId());
        assertFalse(placed.duplicate());
        server.verify();
    }

    @Test
    void doesNotRetryARejection() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"VAL-422\",\"message\":\"Insufficient funds\"}"));

        OrderPlacementException e = assertThrows(OrderPlacementException.class, this::place);

        assertEquals("VAL-422", e.getErrorCode());
        assertEquals("Insufficient funds", e.getMessage());
        server.verify();
    }

    @Test
    void treatsAReusedIdempotencyKeyAsAlreadyPlaced() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"ORD-409\",\"message\":\"already processed\"}"));

        TradeApiOrderClient.PlacedOrder placed = place();

        assertTrue(placed.duplicate());
        assertNull(placed.orderId());
    }

    @Test
    void retriesAServerErrorOnceWithTheSameKey() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders")).andRespond(withServerError());
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andExpect(jsonPath("$.idempotencyKey").value(KEY))
            .andRespond(withSuccess("{\"orderId\":\"ORD-xyz\"}", MediaType.APPLICATION_JSON));

        assertEquals("ORD-xyz", place().orderId());
        server.verify();
    }

    @Test
    void givesUpAfterTwoServerErrors() {
        server.expect(times(2), requestTo("http://trade-api/api/v1/orders")).andRespond(withServerError());

        OrderPlacementException e = assertThrows(OrderPlacementException.class, this::place);

        assertEquals(TradeApiOrderClient.UNAVAILABLE, e.getErrorCode());
        server.verify();
    }

    @Test
    void reportsAnHttpCodeWhenTheErrorHasNoBody() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders")).andRespond(withResourceNotFound());

        assertEquals("HTTP-404", assertThrows(OrderPlacementException.class, this::place).getErrorCode());
    }

    private static org.springframework.test.web.client.ResponseCreator unreachable() {
        return request -> {
            throw new java.net.ConnectException("connection refused");
        };
    }

    @Test
    void retriesOnceWhenTheTradeApiIsUnreachable() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders")).andRespond(unreachable());
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withSuccess("{\"orderId\":\"ORD-retry\"}", MediaType.APPLICATION_JSON));

        assertEquals("ORD-retry", place().orderId());
        server.verify();
    }

    @Test
    void givesUpWhenTheTradeApiStaysUnreachable() {
        server.expect(times(2), requestTo("http://trade-api/api/v1/orders")).andRespond(unreachable());

        OrderPlacementException e = assertThrows(OrderPlacementException.class, this::place);

        assertTrue(e.getMessage().contains("unreachable"));
        server.verify();
    }

    @Test
    void aRetryThatIsRejectedOrDuplicateIsReportedAsSuch() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders")).andRespond(withServerError());
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"VAL-422\",\"message\":\"Market closed\"}"));

        OrderPlacementException e = assertThrows(OrderPlacementException.class, this::place);

        assertEquals("VAL-422", e.getErrorCode());
        server.verify();
    }

    @Test
    void anUnreadableErrorBodyFallsBackToTheHttpStatus() {
        server.expect(once(), requestTo("http://trade-api/api/v1/orders"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML).body("<html>nope</html>"));

        OrderPlacementException e = assertThrows(OrderPlacementException.class, this::place);

        assertEquals("HTTP-400", e.getErrorCode());
    }

    @Test
    void theSpringConstructorAppliesTheBaseUrlAndTimeout() {
        TradeApiOrderClient configured = new TradeApiOrderClient(RestClient.builder(), "http://localhost:1", 50);
        assertThrows(OrderPlacementException.class,
            () -> configured.placeOrder("tok", 1L, "AAPL", OrderSide.BUY, 1, BigDecimal.ONE, KEY));
    }
}
