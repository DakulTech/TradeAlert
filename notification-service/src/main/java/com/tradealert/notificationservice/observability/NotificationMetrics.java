package com.tradealert.notificationservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter deliveredCounter;
    private final Counter queuedCounter;
    private final Counter replayedCounter;
    private final Counter failedCounter;
    private final Counter consumedCounter;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.deliveredCounter = Counter.builder("notifications_delivered_total")
                .description("Total number of notifications delivered")
                .register(meterRegistry);
        this.queuedCounter = Counter.builder("notifications_queued_total")
                .description("Total number of notifications queued")
                .register(meterRegistry);
        this.replayedCounter = Counter.builder("notifications_replayed_total")
                .description("Total number of notifications replayed")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("notifications_failed_total")
                .description("Total number of failed notifications")
                .register(meterRegistry);
        this.consumedCounter = Counter.builder("notification_events_consumed_total")
                .description("Alert events consumed by the notification service")
                .register(meterRegistry);
    }

    public void incrementDelivered() {
        deliveredCounter.increment();
    }

    public void incrementQueued() {
        queuedCounter.increment();
    }

    public void incrementReplayed() {
        replayedCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public void incrementConsumed() {
        consumedCounter.increment();
    }
}
