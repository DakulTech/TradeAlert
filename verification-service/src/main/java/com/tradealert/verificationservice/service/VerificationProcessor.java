package com.tradealert.verificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
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
    private final Tracer tracer;

    public VerificationProcessor(RedisTemplate<String, String> redisTemplate,
            EmailSender emailSender,
            VerificationMetrics metrics,
            Tracer tracer) {
        this.redisTemplate = redisTemplate;
        this.emailSender = emailSender;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @KafkaListener(topics = "user-registered", groupId = "verification-service")
    public void handleUserRegistered(UserRegisteredEvent event) {
        sendVerification(event);
    }

    public void sendVerification(UserRegisteredEvent event) {
        Span span = tracer.spanBuilder("verification.send_email").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", event.getUserId());
            span.setAttribute("user.email", event.getEmail());
            String token = UUID.randomUUID().toString();
            String key = "verify:token:" + token;

            redisTemplate.opsForValue().set(key, event.getUserId().toString(), 15, TimeUnit.MINUTES);

            String verificationLink = "https://your-app.com/verify"
                    + "?userId=" + event.getUserId() + "&token=" + token;

            emailSender.send(event.getEmail(), "Verify your account",
                    "Hello,\n\nPlease verify your account by clicking the link below:\n"
                            + verificationLink + "\n\nThanks!");

            metrics.incrementSent();
            span.addEvent("verification_email_sent");
        } catch (Exception e) {
            metrics.incrementFailed();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Verification email failed");
            log.error("Failed to send verification email to {}: {}", event.getEmail(), e.getMessage(), e);
        } finally {
            span.end();
        }
    }

    public boolean confirmVerification(Long userId, String token) {
        Span span = tracer.spanBuilder("verification.confirm").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", userId);
            String key = "verify:token:" + token;
            String storedUserId = redisTemplate.opsForValue().get(key);

            if (storedUserId != null && storedUserId.equals(userId.toString())) {

                redisTemplate.delete(key);
                metrics.incrementConfirmed();
                span.addEvent("verification_confirmed");
                return true;
            } else {
                metrics.incrementFailed();
                span.setStatus(StatusCode.ERROR, "Invalid or expired verification token");
                span.addEvent("verification_token_invalid_or_expired");
                return false;
            }
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Verification confirmation failed");
            throw new IllegalStateException("Verification confirmation failed", exception);
        } finally {
            span.end();
        }
    }
}
