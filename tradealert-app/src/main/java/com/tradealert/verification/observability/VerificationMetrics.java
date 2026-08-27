package com.tradealert.verification.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class VerificationMetrics {

    private final Counter sent;
    private final Counter confirmed;
    private final Counter failed;

    public VerificationMetrics(MeterRegistry meterRegistry) {
        sent = Counter.builder("verifications_sent_total").register(meterRegistry);
        confirmed = Counter.builder("verifications_confirmed_total").register(meterRegistry);
        failed = Counter.builder("verifications_failed_total").register(meterRegistry);
    }

    public void incrementSent() {
        sent.increment();
    }

    public void incrementConfirmed() {
        confirmed.increment();
    }

    public void incrementFailed() {
        failed.increment();
    }
}
