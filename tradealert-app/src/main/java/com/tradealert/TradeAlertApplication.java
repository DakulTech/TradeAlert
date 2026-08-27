package com.tradealert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeAlertApplication.class, args);
    }
}
