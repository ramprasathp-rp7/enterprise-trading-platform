package com.tradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for proper serialization of Java 8 types.
 * Registers modules to handle Optional<T> and java.time types.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());      // Handle Optional<T>
        mapper.registerModule(new JavaTimeModule());  // Handle Instant, LocalDateTime, etc.
        return mapper;
    }
}
