package com.tradingplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({
    "com.tradingplatform.api.mappers",
    "com.tradingplatform.watchlist.mapper",
    "com.tradingplatform.watchlist.alerts.mapper",
    "com.tradingplatform.notifications.mapper",
    "com.tradingplatform.strategy.mapper",
    "com.tradingplatform.settings.mapper"
})
public class TradeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApiApplication.class, args);
    }
}

