package com.tradealert.userservice.service;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.tradealert.userservice.model.User;
import com.tradealert.userservice.repository.UserRepository;
import com.tradealert.userservice.security.JwtUtil;
import com.tradealert.userservice.event.UserRegisteredEvent;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Tracer tracer;
    private final Counter failedLoginCounter;
    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final Counter registrationsCounter;
    private final Counter successfulLoginsCounter;
    private final Counter logoutsCounter;

    public AuthService(UserRepository userRepository,
            JwtUtil jwtUtil,
            Tracer tracer,
            MeterRegistry meterRegistry,
            KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate,
            RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.tracer = tracer;
        this.failedLoginCounter = Counter.builder("logins_failed_total").register(meterRegistry);
        this.registrationsCounter = Counter.builder("registrations_total")
                .description("User registration attempts")
                .register(meterRegistry);
        this.successfulLoginsCounter = Counter.builder("logins_successful_total")
                .description("Successful user logins")
                .register(meterRegistry);
        this.logoutsCounter = Counter.builder("logouts_total")
                .description("User logout requests")
                .register(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    public String register(User user) {
        Span span = tracer.spanBuilder("registerUser").startSpan();
        try (var scope = span.makeCurrent()) {
            registrationsCounter.increment();
            span.setAttribute("user.email", user.getEmail());
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
            user.setVerified(false);
            user.setCreatedAt(Instant.now());
            userRepository.save(user);

            UserRegisteredEvent event = new UserRegisteredEvent();
            event.setUserId(user.getId());
            event.setEmail(user.getEmail());
            event.setTimestamp(Instant.now());
            kafkaTemplate.send("user-registered", event);

            return jwtUtil.generateToken(user.getId().toString());
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "User registration failed");
            throw new IllegalStateException("User registration failed", exception);
        } finally {
            span.end();
        }
    }

    public String login(String email, String password, String device, String location) {
        Span span = tracer.spanBuilder("loginUser").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("login.device", device == null ? "unknown" : device);
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
                Long userId = userOpt.get().getId();

                String key = "login:failures:" + userId;
                Long failures = redisTemplate.opsForValue().increment(key, 0);
                if (failures != null && failures >= 5) {
                    throw new RuntimeException("Account temporarily locked due to failed attempts");
                }

                String lastLoginKey = "login:last:" + userId;
                String lastLogin = redisTemplate.opsForValue().get(lastLoginKey);
                if (lastLogin != null && !lastLogin.equals(location)) {
                    throw new RuntimeException("Unusual login detected, step-up authentication required");
                }

                String token = jwtUtil.generateToken(userId.toString());
                redisTemplate.opsForValue().set("session:" + userId, token);
                redisTemplate.opsForValue().set(lastLoginKey, location);
                successfulLoginsCounter.increment();
                span.setAttribute("user.id", userId);

                return token;
            }
            failedLoginCounter.increment();
            throw new RuntimeException("Invalid credentials");
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "User login failed");
            throw new IllegalStateException("User login failed", exception);
        } finally {
            span.end();
        }
    }

    public void logout(Long userId) {
        Span span = tracer.spanBuilder("logoutUser").startSpan();
        try (var scope = span.makeCurrent()) {
            logoutsCounter.increment();
            span.setAttribute("user.id", userId);
            redisTemplate.delete("session:" + userId);
            redisTemplate.delete("login:failures:" + userId);
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "User logout failed");
            throw new IllegalStateException("User logout failed", exception);
        } finally {
            span.end();
        }
    }
}
