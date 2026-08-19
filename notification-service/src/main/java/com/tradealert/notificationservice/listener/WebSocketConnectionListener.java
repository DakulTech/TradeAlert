package com.tradealert.notificationservice.listener;

import com.tradealert.notificationservice.service.PendingNotificationService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
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
    private final Tracer tracer;

    public WebSocketConnectionListener(PendingNotificationService pendingNotificationService, Tracer tracer) {
        this.pendingNotificationService = pendingNotificationService;
        this.tracer = tracer;
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        Span span = tracer.spanBuilder("notification.websocket_connect").startSpan();
        try (var scope = span.makeCurrent()) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            Object userIdObj = accessor.getSessionAttributes().get("userId");
            if (userIdObj != null) {
                Long userId = userIdObj instanceof Long ? (Long) userIdObj : Long.parseLong(userIdObj.toString());
                span.setAttribute("user.id", userId);
                span.addEvent("websocket_user_reconnected");
                pendingNotificationService.replayPendingNotifications(userId);
            }
        } finally {
            span.end();
        }
    }
}
