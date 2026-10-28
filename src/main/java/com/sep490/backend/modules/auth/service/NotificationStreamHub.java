package com.sep490.backend.modules.auth.service;

import com.sep490.backend.modules.auth.dto.response.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Kênh đẩy thông báo Realtime (SSE - Server-Sent Events) cho ứng dụng Hola Vietnamese.
 * Giữ các kết nối SSE đang mở theo từng người dùng để khi có thông báo mới (học tập, bài thi, hệ thống),
 * chuông thông báo trên Frontend/PWA sẽ sáng lên ngay lập tức mà không cần polling liên tục.
 */
@Component
@Slf4j
public class NotificationStreamHub {

    public static final String EVENT_CREATED = "notification.created";
    public static final String EVENT_READ = "notification.read";

    public static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    public static final long HEARTBEAT_MS = 25_000L;

    private final Map<Long, Collection<SseEmitter>> connections = new ConcurrentHashMap<>();

    /**
     * Mở một kết nối SSE mới cho người dùng.
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        connections.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onError(throwable -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            complete(emitter);
        });

        // Gửi ngay 1 event kết nối thành công đầu tiên
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to Hola Vietnamese Notification Stream"));
        } catch (IOException e) {
            remove(userId, emitter);
        }

        return emitter;
    }

    /**
     * Đẩy thông báo mới tới mọi tab/thiết bị đang mở của người dùng.
     */
    public void push(Long userId, NotificationResponse payload) {
        send(userId, SseEmitter.event().name(EVENT_CREATED).data(payload));
    }

    /**
     * Đẩy sự kiện đã đọc để các tab còn lại đồng bộ badge số thông báo chưa đọc.
     */
    public void pushRead(Long userId, Long notificationId) {
        send(userId, SseEmitter.event().name(EVENT_READ).data(Map.of("notificationId", notificationId)));
    }

    /**
     * Nhịp tim (Heartbeat/Ping) định kỳ mỗi 25s để giữ kết nối SSE sống qua các proxy/router.
     */
    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        connections.forEach((userId, emitters) ->
                deliver(userId, emitters, SseEmitter.event().comment("ping")));
    }

    /**
     * Đếm số lượng kết nối đang mở của một user.
     */
    public int connectionCount(Long userId) {
        Collection<SseEmitter> emitters = connections.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    private void send(Long userId, SseEmitter.SseEventBuilder event) {
        Collection<SseEmitter> emitters = connections.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        deliver(userId, emitters, event);
    }

    private void deliver(Long userId, Collection<SseEmitter> emitters, SseEmitter.SseEventBuilder event) {
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
            log.debug("Kết thúc kết nối SSE đã đóng sẵn: {}", exception.getMessage());
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
