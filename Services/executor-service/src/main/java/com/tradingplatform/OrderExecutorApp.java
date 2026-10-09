package com.tradingplatform;

import org.springframework.context.annotation.Bean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;

import com.tradingplatform.quote.FauxnanceQuoteClient;
import com.tradingplatform.quote.QuoteClient;

@SpringBootApplication
@MapperScan("com.tradingplatform.mappers")
public class OrderExecutorApp {
    public static void main(String[] args) {
        SpringApplication.run(OrderExecutorApp.class, args);
    }

    @Bean
    public QuoteClient quoteClient(@Value("${fauxnance.base-url}") String baseUrl,
                                   @Value("${fauxnance.api-key:}") String apiKey) {
        return new FauxnanceQuoteClient(baseUrl, apiKey, 3, 250L, 2000L);
    }
}
