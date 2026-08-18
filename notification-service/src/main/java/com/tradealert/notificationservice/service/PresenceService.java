package com.tradealert.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {
    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final ConcurrentHashMap<Long, Boolean> onlineUsers = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("userId");
        if (userIdHeader != null) {
            Long userId = Long.valueOf(userIdHeader);
            onlineUsers.put(userId, true);
            log.info("User {} connected", userId);
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("userId");
        if (userIdHeader != null) {
            Long userId = Long.valueOf(userIdHeader);
            onlineUsers.remove(userId);
            log.info("User {} disconnected", userId);
        }
    }

    public boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }
}
