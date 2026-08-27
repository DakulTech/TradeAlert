package com.tradealert.notifications.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
public class PresenceService {

    private static final String KEY_PREFIX = "presence:";
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;

    public PresenceService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void connect(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        String key = presenceKey(userId);
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, PRESENCE_TTL);
    }

    public void disconnect(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        String key = presenceKey(userId);
        redisTemplate.opsForSet().remove(key, sessionId);
        Long remaining = redisTemplate.opsForSet().size(key);
        if (remaining != null && remaining == 0) {
            redisTemplate.delete(key);
        }
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
