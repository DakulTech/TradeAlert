package com.tradealert.notificationservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
public class PresenceService {
    private static final String KEY_PREFIX = "presence:";
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final Tracer tracer;
    private final Counter connectedUsers;
    private final Counter disconnectedUsers;

    public PresenceService(RedisTemplate<String, String> redisTemplate,
            Tracer tracer,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.tracer = tracer;
        this.connectedUsers = Counter.builder("websocket_connections_total")
                .description("WebSocket connection events")
                .register(meterRegistry);
        this.disconnectedUsers = Counter.builder("websocket_disconnections_total")
                .description("WebSocket disconnection events")
                .register(meterRegistry);
    }

    public void connect(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }

        String key = presenceKey(userId);
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, PRESENCE_TTL);
        connectedUsers.increment();
        Span.current().setAttribute("user.id", userId).addEvent("websocket_connected");
    }

    public void disconnect(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }

        String key = presenceKey(userId);
        redisTemplate.opsForSet().remove(key, sessionId);
        Long remainingSessions = redisTemplate.opsForSet().size(key);
        if (remainingSessions != null && remainingSessions == 0) {
            redisTemplate.delete(key);
        }
        disconnectedUsers.increment();
        Span span = tracer.spanBuilder("notification.websocket_disconnect").startSpan();
        span.setAttribute("user.id", userId).addEvent("websocket_disconnected");
        span.end();
    }

    public boolean isUserOnline(Long userId) {
        if (userId == null) {
            return false;
        }

        Set<String> sessions = redisTemplate.opsForSet().members(presenceKey(userId));
        return sessions != null && !sessions.isEmpty();
    }

    private String presenceKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
