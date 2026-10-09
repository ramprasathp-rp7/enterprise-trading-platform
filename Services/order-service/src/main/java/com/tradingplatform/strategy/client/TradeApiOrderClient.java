package com.tradingplatform.strategy.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.enums.OrderSide;

/**
 * Places a strategy's orders through the Trade REST API, POST /api/v1/orders, over HTTP like any
 * other client. Never through OrderService or the orders topic directly: the route's validation,
 * account authorisation and idempotency check are what stand between a strategy bug and a bad position.
 *
 * - 201: the order id is returned.
 * - 409 ORD-409 (idempotency key already used): an earlier attempt for the same trigger got through,
 *   so this counts as placed.
 * - other 4xx: a rejection, thrown without retrying (retrying cannot change the answer).
 * - 5xx or no answer: retried once with the same idempotency key, then thrown.
 */
@Component
public class TradeApiOrderClient {

    private static final Logger logger = LoggerFactory.getLogger(TradeApiOrderClient.class);
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String DUPLICATE = "ORD-409";

    /** Result of a placement: the order id, and whether it was placed by an earlier attempt */
    public record PlacedOrder(String orderId, boolean duplicate) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TradeApiOrderClient(RestClient.Builder builder,
                               @Value("${trading.strategy.trade-api-url}") String baseUrl,
                               @Value("${trading.strategy.order-timeout-ms:5000}") long timeoutMs) {
        this(builder.clone()
            .baseUrl(baseUrl)
            .requestFactory(timeouts(Duration.ofMillis(timeoutMs)))
            .build());
    }

    TradeApiOrderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public PlacedOrder placeOrder(String bearerToken, long accountId, String symbol, OrderSide side, int quantity,
                                  BigDecimal price, String idempotencyKey) {
        Map<String, Object> body = Map.of(
            "accountId", accountId,
            "symbol", symbol,
            "side", side.name(),
            "quantity", quantity,
            "price", price,
            "idempotencyKey", idempotencyKey);

        try {
            return send(bearerToken, body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                logger.warn("Trade API answered {} for {}; retrying once", e.getStatusCode().value(), idempotencyKey);
                return retry(bearerToken, body);
            }
            return rejectedOrDuplicate(e);
        } catch (ResourceAccessException e) {
            logger.warn("Trade API unreachable for {}; retrying once", idempotencyKey);
            return retry(bearerToken, body);
        }
    }

    private PlacedOrder retry(String bearerToken, Map<String, Object> body) {
        try {
            return send(bearerToken, body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new OrderPlacementException(errorCode(e, UNAVAILABLE), errorMessage(e), e);
            }
            return rejectedOrDuplicate(e);
        } catch (ResourceAccessException e) {
            throw new OrderPlacementException(UNAVAILABLE, "Trade API unreachable: " + e.getMessage(), e);
        }
    }

    private PlacedOrder send(String bearerToken, Map<String, Object> body) {
        JsonNode response = restClient.post()
            .uri("/api/v1/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);
        String orderId = response != null ? response.path("orderId").asText(null) : null;
        return new PlacedOrder(orderId, false);
    }

    private PlacedOrder rejectedOrDuplicate(RestClientResponseException e) {
        String code = errorCode(e, "HTTP-" + e.getStatusCode().value());
        if (DUPLICATE.equals(code)) {
            return new PlacedOrder(null, true);
        }
        throw new OrderPlacementException(code, errorMessage(e), e);
    }

    private String errorCode(RestClientResponseException e, String fallback) {
        JsonNode node = errorBody(e);
        return node != null && node.hasNonNull("errorCode") ? node.get("errorCode").asText() : fallback;
    }

    private String errorMessage(RestClientResponseException e) {
        JsonNode node = errorBody(e);
        return node != null && node.hasNonNull("message") ? node.get("message").asText()
            : "Trade API answered " + e.getStatusCode().value();
    }

    private JsonNode errorBody(RestClientResponseException e) {
        try {
            return objectMapper.readTree(e.getResponseBodyAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
