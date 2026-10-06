package com.tradingplatform.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = SecurityFilterChainAuthFlowTest.TestConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=test-shared-secret-32-bytes-minimum-key",
        "JWT_ISSUER=auth-service"
})
class SecurityFilterChainAuthFlowTest {

    private static final String SECRET =
            "test-shared-secret-32-bytes-minimum-key";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void missingHeaderReturnsContractUnauthorisedBody() throws Exception {

        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}"
                ));
    }


    @Test
    void wrongSchemeReturnsContractUnauthorisedBody() throws Exception {

        mockMvc.perform(
                get("/api/v1/probe")
                        .header("Authorization", "Basic abc")
        )
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}"
                ));
    }


    @Test
    void expiredTokenReturnsContractUnauthorisedBody() throws Exception {

        String token = createToken(
                "HS256",
                Instant.now().minusSeconds(1).getEpochSecond(),
                1L
        );

        mockMvc.perform(
                get("/api/v1/probe")
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}"
                ));
    }


    @Test
    void forgedSignatureReturnsContractUnauthorisedBody() throws Exception {

        String validToken = createToken(
                "HS256",
                Instant.now().plusSeconds(900).getEpochSecond(),
                1L
        );

        String[] tokenParts = validToken.split("\\.");

        String forgedPayload = base64Url(
                "{\"sub\":\"u-1\",\"accountId\":2,\"roles\":[\"CUSTOMER\"]," +
                "\"iat\":1750000000,\"exp\":1790000900,\"iss\":\"auth-service\"}"
        );

        String forgedToken =
                tokenParts[0] + "." +
                forgedPayload + "." +
                tokenParts[2];

        mockMvc.perform(
                get("/api/v1/probe")
                        .header("Authorization", "Bearer " + forgedToken)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"errorCode\":\"AUTH-401\",\"message\":\"Unauthorised\"}"
                ));
    }


    @Test
    void validTokenAllowsRequest() throws Exception {

        String token = createToken(
                "HS256",
                Instant.now().plusSeconds(900).getEpochSecond(),
                1L
        );

        mockMvc.perform(
                get("/api/v1/probe")
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json(
                        "{\"errorCode\":\"VAL-422\",\"message\":\"Invalid input\"}"
                ));
    }


    private String createToken(
            String alg,
            long exp,
            long accountId) {

        long issuedAt = Instant.now().getEpochSecond();

        String header = base64Url(
                toJson(Map.of(
                        "alg", alg,
                        "typ", "JWT"
                ))
        );

        String payload = base64Url(
                toJson(Map.of(
                        "sub", "u-1",
                        "accountId", accountId,
                        "roles", List.of("CUSTOMER"),
                        "iat", issuedAt,
                        "exp", exp,
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

            mac.init(
                    new SecretKeySpec(
                            SECRET.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"
                    )
            );

            byte[] signature =
                    mac.doFinal(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(signature);

        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }


    private String base64Url(String raw) {

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        raw.getBytes(StandardCharsets.UTF_8)
                );
    }


    @Configuration
    @EnableAutoConfiguration
    @Import({
            ApiSecurityConfiguration.class,
            JwtAuthenticationConverter.class,
            ResolverAuthenticationEntryPoint.class,
            GlobalExceptionHandler.class
    })
    static class TestConfig {
        @Bean
        public ProbeController probeController() {
            return new ProbeController();
        }
    }


    @RestController
    @RequestMapping("/api/v1/probe")
    public static class ProbeController {

        @GetMapping
        String probe() throws Exception {
            // Test expects 422 for probe endpoint - simulate validation error
            throw new com.tradingplatform.domain.exception.InvalidOrderException("Invalid input");
        }
    }
}