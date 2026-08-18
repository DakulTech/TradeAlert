package com.tradealert.verificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.tradealert.verificationservice.observability.VerificationMetrics;
import com.tradealert.verificationservice.events.UserRegisteredEvent;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class VerificationProcessor {
    private static final Logger log = LoggerFactory.getLogger(VerificationProcessor.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailSender emailSender;
    private final VerificationMetrics metrics;

    public VerificationProcessor(RedisTemplate<String, String> redisTemplate,
            EmailSender emailSender,
            VerificationMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.emailSender = emailSender;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "user-registered", groupId = "verification-service")
    public void handleUserRegistered(UserRegisteredEvent event) {
        sendVerification(event);
    }

    public void sendVerification(UserRegisteredEvent event) {
        try {
            String token = UUID.randomUUID().toString();
            String key = "verify:token:" + token;

            redisTemplate.opsForValue().set(key, event.getUserId().toString(), 15, TimeUnit.MINUTES);

            String verificationLink = "https://your-app.com/verify"
                    + "?userId=" + event.getUserId() + "&token=" + token;

            emailSender.send(event.getEmail(), "Verify your account",
                    "Hello,\n\nPlease verify your account by clicking the link below:\n"
                            + verificationLink + "\n\nThanks!");

            metrics.incrementSent();
            log.info("Verification email sent to {} with token {}", event.getEmail(), token);
        } catch (Exception e) {
            metrics.incrementFailed();
            log.error("Failed to send verification email to {}: {}", event.getEmail(), e.getMessage(), e);
        }
    }

    public boolean confirmVerification(Long userId, String token) {
        String key = "verify:token:" + token;
        String storedUserId = redisTemplate.opsForValue().get(key);

        if (storedUserId != null && storedUserId.equals(userId.toString())) {

            redisTemplate.delete(key);
            metrics.incrementConfirmed();
            log.info("User {} verified successfully", userId);
            return true;
        } else {
            metrics.incrementFailed();
            log.warn("Verification failed for user {} with token {}", userId, token);
            return false;
        }
    }
}
