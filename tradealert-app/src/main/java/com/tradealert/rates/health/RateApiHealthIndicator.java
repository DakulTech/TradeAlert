package com.tradealert.rates.health;

import com.tradealert.rates.service.RateService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RateApiHealthIndicator implements HealthIndicator {

    private final RateService rateService;

    public RateApiHealthIndicator(RateService rateService) {
        this.rateService = rateService;
    }

    @Override
    public Health health() {
        Map<String, Boolean> status = rateService.checkApiHealth();
        boolean primary = status.getOrDefault("primaryApi", false);
        boolean secondary = status.getOrDefault("secondaryApi", false);
        if (primary && secondary) {
            return Health.up().withDetails(status).build();
        }
        if (primary || secondary) {
            return Health.status("DEGRADED").withDetails(status).build();
        }
        return Health.down().withDetails(status).build();
    }
}
