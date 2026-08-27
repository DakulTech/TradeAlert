package com.tradealert.verification.service;

import com.tradealert.identity.model.User;
import com.tradealert.identity.repository.UserRepository;
import com.tradealert.verification.observability.VerificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final String TOKEN_PREFIX = "verify:token:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final VerificationMetrics metrics;

    public VerificationService(RedisTemplate<String, String> redisTemplate,
            UserRepository userRepository, EmailSender emailSender,
            VerificationMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.metrics = metrics;
    }

    public void sendVerification(User user) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token,
                user.getId().toString(), TOKEN_TTL);
        String link = "https://your-app.com/verify?userId=" + user.getId() + "&token=" + token;
        try {
            emailSender.send(user.getEmail(), "Verify your account",
                    "Hello,\n\nPlease verify your account by clicking the link below:\n"
                            + link + "\n\nThanks!");
            metrics.incrementSent();
        } catch (Exception exception) {
            metrics.incrementFailed();
            log.error("Failed to send verification email to {}", user.getEmail(), exception);
        }
    }

    @Transactional
    public boolean confirmVerification(Long userId, String token) {
        String storedUserId = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (storedUserId == null || !storedUserId.equals(userId.toString())) {
            metrics.incrementFailed();
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            metrics.incrementFailed();
            return false;
        }

        user.setVerified(true);
        userRepository.save(user);
        redisTemplate.delete(TOKEN_PREFIX + token);
        metrics.incrementConfirmed();
        return true;
    }
}
