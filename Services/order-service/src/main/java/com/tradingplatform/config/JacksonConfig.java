package com.tradingplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.web.client.RestTemplate;
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
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Serialize Instant as ISO-8601 string
        return mapper;
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
