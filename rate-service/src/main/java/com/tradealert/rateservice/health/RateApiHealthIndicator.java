package com.tradealert.rateservice.health;

import com.tradealert.rateservice.service.RateService;
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

        boolean primaryHealthy = status.getOrDefault("primaryApi", false);
        boolean secondaryHealthy = status.getOrDefault("secondaryApi", false);

        if (primaryHealthy && secondaryHealthy) {
            return Health.up()
                    .withDetail("primaryApi", "UP")
                    .withDetail("secondaryApi", "UP")
                    .build();
        } else if (primaryHealthy) {
            return Health.status("DEGRADED")
                    .withDetail("primaryApi", "UP")
                    .withDetail("secondaryApi", "DOWN")
                    .build();
        } else if (secondaryHealthy) {
            return Health.status("DEGRADED")
                    .withDetail("primaryApi", "DOWN")
                    .withDetail("secondaryApi", "UP")
                    .build();
        } else {
            return Health.down()
                    .withDetail("primaryApi", "DOWN")
                    .withDetail("secondaryApi", "DOWN")
                    .build();
        }
    }
}
