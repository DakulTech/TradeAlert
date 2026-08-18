package com.tradealert.verificationservice.consumer;

import com.tradealert.verificationservice.events.UserRegisteredEvent;
import com.tradealert.verificationservice.service.VerificationProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventConsumer {

    private final VerificationProcessor processor;

    public UserEventConsumer(VerificationProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "user-registered", groupId = "verification-service")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        processor.sendVerification(event);
    }
}
