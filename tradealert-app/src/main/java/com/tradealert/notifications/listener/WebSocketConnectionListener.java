package com.tradealert.notifications.listener;

import com.tradealert.notifications.service.NotificationService;
import com.tradealert.notifications.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketConnectionListener {

    private final NotificationService notificationService;
    private final PresenceService presenceService;

    public WebSocketConnectionListener(NotificationService notificationService,
            PresenceService presenceService) {
        this.notificationService = notificationService;
        this.presenceService = presenceService;
    }

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userId(accessor);
        if (userId != null) {
            presenceService.connect(userId, accessor.getSessionId());
            notificationService.replayPending(userId);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userId(accessor);
        if (userId != null) {
            presenceService.disconnect(userId, accessor.getSessionId());
        }
    }

    private Long userId(StompHeaderAccessor accessor) {
        Object value = accessor.getSessionAttributes() == null
                ? null
                : accessor.getSessionAttributes().get("userId");
        if (value == null) {
            return null;
        }
        try {
            return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
