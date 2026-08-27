package com.tradealert.notifications.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter delivered;
    private final Counter queued;
    private final Counter replayed;
    private final Counter failed;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        delivered = Counter.builder("notifications_delivered_total").register(meterRegistry);
        queued = Counter.builder("notifications_queued_total").register(meterRegistry);
        replayed = Counter.builder("notifications_replayed_total").register(meterRegistry);
        failed = Counter.builder("notifications_failed_total").register(meterRegistry);
    }

    public void incrementDelivered() {
        delivered.increment();
    }

    public void incrementQueued() {
        queued.increment();
    }

    public void incrementReplayed() {
        replayed.increment();
    }

    public void incrementFailed() {
        failed.increment();
    }
}
