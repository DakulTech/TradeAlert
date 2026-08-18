package com.tradealert.verificationservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class VerificationMetrics {

    private final Counter verificationsSent;
    private final Counter verificationsConfirmed;
    private final Counter verificationsFailed;

    public VerificationMetrics(MeterRegistry registry) {
        this.verificationsSent = Counter.builder("verifications_sent_total")
                .description("Total verification emails sent")
                .tag("service", "verification-service")
                .register(registry);

        this.verificationsConfirmed = Counter.builder("verifications_confirmed_total")
                .description("Total verifications successfully confirmed")
                .tag("service", "verification-service")
                .register(registry);

        this.verificationsFailed = Counter.builder("verifications_failed_total")
                .description("Total verifications failed or expired")
                .tag("service", "verification-service")
                .register(registry);
    }

    public void incrementSent() {
        verificationsSent.increment();
    }

    public void incrementConfirmed() {
        verificationsConfirmed.increment();
    }

    public void incrementFailed() {
        verificationsFailed.increment();
    }
}
