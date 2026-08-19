package com.tradealert.notificationservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {
    private final ConcurrentHashMap<Long, Boolean> onlineUsers = new ConcurrentHashMap<>();
    private final Tracer tracer;
    private final Counter connectedUsers;
    private final Counter disconnectedUsers;

    public PresenceService(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.connectedUsers = Counter.builder("websocket_connections_total")
                .description("WebSocket connection events")
                .register(meterRegistry);
        this.disconnectedUsers = Counter.builder("websocket_disconnections_total")
                .description("WebSocket disconnection events")
                .register(meterRegistry);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("userId");
        if (userIdHeader != null) {
            Long userId = Long.valueOf(userIdHeader);
            onlineUsers.put(userId, true);
            connectedUsers.increment();
            Span.current().setAttribute("user.id", userId).addEvent("websocket_connected");
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("userId");
        if (userIdHeader != null) {
            Long userId = Long.valueOf(userIdHeader);
            onlineUsers.remove(userId);
            disconnectedUsers.increment();
            Span span = tracer.spanBuilder("notification.websocket_disconnect").startSpan();
            span.setAttribute("user.id", userId).addEvent("websocket_disconnected");
            span.end();
        }
    }

    public boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }
}
