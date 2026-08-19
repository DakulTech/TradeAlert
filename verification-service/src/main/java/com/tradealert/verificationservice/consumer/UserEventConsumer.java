package com.tradealert.verificationservice.consumer;

import com.tradealert.verificationservice.events.UserRegisteredEvent;
import com.tradealert.verificationservice.service.VerificationProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@Component
public class UserEventConsumer {

    private final VerificationProcessor processor;
    private final Tracer tracer;

    public UserEventConsumer(VerificationProcessor processor, Tracer tracer) {
        this.processor = processor;
        this.tracer = tracer;
    }

    @KafkaListener(topics = "user-registered", groupId = "verification-service")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        Span span = tracer.spanBuilder("verification.consume_user_registered").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", event.getUserId());
            processor.sendVerification(event);
        } finally {
            span.end();
        }
    }
}
