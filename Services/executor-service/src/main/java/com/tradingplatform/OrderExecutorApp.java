package com.tradingplatform;

import org.springframework.context.annotation.Bean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import com.tradingplatform.quote.FauxnanceQuoteClient;
import com.tradingplatform.quote.QuoteClient;

@SpringBootApplication
@MapperScan("com.tradingplatform.mappers")
public class OrderExecutorApp {
    private static final String DEFAULT_FAUXNANCE_BASE_URL = "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1";

    OrderExecutorApp(Environment env) {
        this.env = env;
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderExecutorApp.class, args);
    }

    private final Environment env;

    @Bean
    public QuoteClient quoteClient() {
        String baseUrl = env.getProperty("FAUXNANCE_BASE_URL", DEFAULT_FAUXNANCE_BASE_URL);
        String apiKey = env.getProperty("FAUXNANCE_API_KEY");
        return new FauxnanceQuoteClient(baseUrl, apiKey, 3, 250L, 2000L);
    }
}
