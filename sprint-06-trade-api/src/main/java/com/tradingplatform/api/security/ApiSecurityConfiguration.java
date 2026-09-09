package com.tradingplatform.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

@Configuration
public class ApiSecurityConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public AuthTokenVerifier authTokenVerifier(
        @Value("${JWT_SECRET}") String jwtSecret,
        @Value("${JWT_ISSUER:auth-service}") String jwtIssuer
    ) {
        return new AuthTokenVerifier(jwtSecret, jwtIssuer);
    }

    @Bean
    public AuthenticationFilter authenticationFilter(AuthTokenVerifier authTokenVerifier, ObjectMapper objectMapper) {
        return new AuthenticationFilter(authTokenVerifier, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistration(
        AuthenticationFilter authenticationFilter
    ) {
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authenticationFilter);
        registration.setUrlPatterns(List.of("/api/v1/*"));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}


