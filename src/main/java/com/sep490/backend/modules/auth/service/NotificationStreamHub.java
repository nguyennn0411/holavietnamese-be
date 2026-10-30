package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.auth.dto.response.NotificationResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** In-memory SSE fan-out for all open tabs of one user. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationStreamHub {

    public static final String EVENT_CREATED = "notification.created";
    public static final String EVENT_READ = "notification.read";
    public static final String EVENT_READ_ALL = "notification.read-all";
    public static final String EVENT_DELETED = "notification.deleted";

    private final NotificationProperties properties;
    private final Map<Long, Collection<SseEmitter>> connections = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        if (!properties.isEnabled() || !properties.isRealtimeEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Kênh thông báo realtime đang tạm tắt");
        }

        SseEmitter emitter = new SseEmitter(properties.getStreamTimeout().toMillis());
        connections.compute(userId, (key, existing) -> {
            Collection<SseEmitter> emitters = existing == null
                    ? new CopyOnWriteArrayList<>()
                    : existing;
            if (emitters.size() >= Math.max(1, properties.getMaxConnectionsPerUser())) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Đã đạt số kết nối thông báo tối đa cho tài khoản này");
            }
            emitters.add(emitter);
            return emitters;
        });

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onError(throwable -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            complete(emitter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "Connected to Hola Vietnamese Notification Stream")));
        } catch (IOException exception) {
            remove(userId, emitter);
            complete(emitter);
        }
        return emitter;
    }

    public void push(Long userId, NotificationResponse payload) {
        if (realtimeEnabled()) {
            send(userId, SseEmitter.event().name(EVENT_CREATED).data(payload));
        }
    }

    public void pushRead(Long userId, Long notificationId, long unreadCount) {
        if (realtimeEnabled()) {
            send(userId, SseEmitter.event().name(EVENT_READ)
                    .data(Map.of("notificationId", notificationId, "unreadCount", unreadCount)));
        }
    }

    public void pushReadAll(Long userId) {
        if (realtimeEnabled()) {
            send(userId, SseEmitter.event().name(EVENT_READ_ALL).data(Map.of("unreadCount", 0)));
        }
    }

    public void pushDeleted(Long userId, Long notificationId, long unreadCount) {
        if (realtimeEnabled()) {
            send(userId, SseEmitter.event().name(EVENT_DELETED)
                    .data(Map.of("notificationId", notificationId, "unreadCount", unreadCount)));
        }
    }

    @Scheduled(fixedDelayString = "${app.notification.heartbeat-interval:25s}")
    public void heartbeat() {
        if (!realtimeEnabled()) {
            return;
        }
        connections.forEach((userId, emitters) ->
                deliver(userId, emitters, SseEmitter.event().comment("ping")));
    }

    public int connectionCount(Long userId) {
        Collection<SseEmitter> emitters = connections.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    public void disconnect(Long userId) {
        Collection<SseEmitter> emitters = connections.remove(userId);
        if (emitters != null) {
            List.copyOf(emitters).forEach(this::complete);
        }
    }

    private boolean realtimeEnabled() {
        return properties.isEnabled() && properties.isRealtimeEnabled();
    }

    private void send(Long userId, SseEmitter.SseEventBuilder event) {
        Collection<SseEmitter> emitters = connections.get(userId);
        if (emitters != null && !emitters.isEmpty()) {
            deliver(userId, emitters, event);
        }
    }

    private void deliver(
            Long userId,
            Collection<SseEmitter> emitters,
            SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(event);
            } catch (IOException exception) {
                log.debug("Client ngắt kết nối SSE của user {}: {}", userId, exception.getMessage());
                remove(userId, emitter);
            } catch (RuntimeException exception) {
                log.warn("Gỡ kết nối SSE lỗi của user {}: {}", userId, exception.getMessage());
                remove(userId, emitter);
                complete(emitter);
            }
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException exception) {
            log.debug("Kết nối SSE đã đóng: {}", exception.getMessage());
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
