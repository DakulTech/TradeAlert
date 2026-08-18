package com.tradealert.notificationservice.listener;

import com.tradealert.notificationservice.service.PendingNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;

@Component
public class WebSocketConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionListener.class);

    private final PendingNotificationService pendingNotificationService;

    public WebSocketConnectionListener(PendingNotificationService pendingNotificationService) {
        this.pendingNotificationService = pendingNotificationService;
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Object userIdObj = accessor.getSessionAttributes().get("userId");
        if (userIdObj != null) {
            Long userId = userIdObj instanceof Long ? (Long) userIdObj : Long.parseLong(userIdObj.toString());
            log.info("User {} reconnected, replaying pending notifications", userId);
            pendingNotificationService.replayPendingNotifications(userId);
        }
    }
}
