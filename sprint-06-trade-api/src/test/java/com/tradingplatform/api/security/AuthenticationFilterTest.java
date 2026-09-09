package com.tradingplatform.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthenticationFilterTest {

    private static final String SECRET = "test-shared-secret";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        AuthTokenVerifier verifier = new AuthTokenVerifier(SECRET, "auth-service", fixedClock);
        filter = new AuthenticationFilter(verifier, objectMapper);
    }

    @Test
    void missingHeaderReturnsStandardUnauthorisedResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void wrongSchemeReturnsStandardUnauthorisedResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        request.addHeader("Authorization", "Basic abc");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void expiredTokenReturnsStandardUnauthorisedResponse() throws ServletException, IOException {
        String token = createToken("HS256", NOW.minusSeconds(1).getEpochSecond(), "auth-service", 1L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void forgedSignatureReturnsStandardUnauthorisedResponse() throws ServletException, IOException {
        String token = createToken("HS256", NOW.plusSeconds(900).getEpochSecond(), "auth-service", 1L);
        String[] tokenParts = token.split("\\.");
        String forgedPayload = base64Url("{\"sub\":\"u-1\",\"accountId\":2,\"roles\":[\"CUSTOMER\"],\"iat\":1750000000,\"exp\":1750000900,\"iss\":\"auth-service\"}");
        String forgedToken = tokenParts[0] + "." + forgedPayload + "." + tokenParts[2];

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        request.addHeader("Authorization", "Bearer " + forgedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void unsupportedAlgorithmReturnsStandardUnauthorisedResponse() throws ServletException, IOException {
        String token = createToken("HS512", NOW.plusSeconds(900).getEpochSecond(), "auth-service", 1L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void validTokenRunsChainAndStoresAuthenticatedUser() throws ServletException, IOException {
        String token = createToken("HS256", NOW.plusSeconds(900).getEpochSecond(), "auth-service", 7L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/7");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());

        Object attribute = request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        assertNotNull(attribute);
        AuthenticatedUser user = (AuthenticatedUser) attribute;
        assertEquals(7L, user.accountId());
    }

    private String createToken(String alg, long exp, String issuer, long accountId) {
        String header = base64Url(toJson(Map.of("alg", alg, "typ", "JWT")));
        String payload = base64Url(toJson(Map.of(
            "sub", "u-1",
            "accountId", accountId,
            "roles", List.of("CUSTOMER"),
            "iat", NOW.getEpochSecond(),
            "exp", exp,
            "iss", issuer
        )));

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
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}

