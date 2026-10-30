package com.sep490.backend.modules.auth.controller;

import com.sep490.backend.modules.auth.security.CustomJwtDecoder;
import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.auth.dto.request.NotificationBroadcastRequest;
import com.sep490.backend.modules.auth.dto.response.NotificationResponse;
import com.sep490.backend.common.dto.PageResponse;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.auth.service.NotificationService;
import com.sep490.backend.modules.auth.service.NotificationStreamHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamHub streamHub;
    private final UserRepository userRepository;
    private final CustomJwtDecoder jwtDecoder;
    private final NotificationProperties notificationProperties;

    /**
     * Lấy danh sách thông báo của người dùng hiện tại (hỗ trợ phân trang và lọc).
     * GET /api/notifications?page=1&size=10&isRead=false
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "isRead", required = false) Boolean isRead) {

        PageResponse<NotificationResponse> result = notificationService.getUserNotifications(page, size, isRead);
        return ApiResponse.<PageResponse<NotificationResponse>>builder()
                .code(1000)
                .result(result)
                .build();
    }

    /**
     * Lấy toàn bộ thông báo dạng danh sách phẳng.
     * GET /api/notifications/all
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<NotificationResponse>> getAllMyNotifications() {
        List<NotificationResponse> result = notificationService.getUserNotifications();
        return ApiResponse.<List<NotificationResponse>>builder()
                .code(1000)
                .result(result)
                .build();
    }

    /**
     * Lấy số lượng thông báo chưa đọc (dùng cho icon chuông thông báo trên Header/Navbar).
     * GET /api/notifications/unread-count
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> getUnreadCount() {
        long unreadCount = notificationService.countUnread();
        return ApiResponse.<Long>builder()
                .code(1000)
                .result(unreadCount)
                .build();
    }

    /**
     * Đánh dấu 1 thông báo là đã đọc.
     * PUT /api/notifications/{id}/read
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ApiResponse.<Void>builder()
                .code(1000)
                .message("Đã đánh dấu thông báo là đã đọc")
                .build();
    }

    /**
     * Đánh dấu tất cả thông báo của người dùng là đã đọc.
     * PUT /api/notifications/read-all
     */
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.<Void>builder()
                .code(1000)
                .message("Đã đánh dấu tất cả thông báo là đã đọc")
                .build();
    }

    /**
     * Xóa 1 thông báo.
     * DELETE /api/notifications/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ApiResponse.<Void>builder()
                .code(1000)
                .message("Đã xóa thông báo")
                .build();
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Integer> broadcast(@Valid @RequestBody NotificationBroadcastRequest request) {
        int recipients = notificationService.broadcast(NotificationMessage.standard(
                request.title(), request.message(), request.type(), request.linkUrl()));
        return ApiResponse.<Integer>builder()
                .code(1000)
                .message("Đã tạo thông báo cho " + recipients + " người dùng")
                .result(recipients)
                .build();
    }

    /**
     * Kênh SSE Realtime Stream giữ kết nối để đẩy thông báo ngay lập tức tới PWA/Browser.
     * GET /api/notifications/stream (Hỗ trợ cả Bearer Header hoặc ?token=... query parameter cho EventSource).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(name = "token", required = false) String tokenParam) {
        Long userId = resolveUserId(tokenParam);
        log.info("Khởi tạo kết nối SSE notification stream cho user id: {}", userId);
        return streamHub.subscribe(userId);
    }

    /**
     * Endpoint tiện ích để test gửi thông báo thử nghiệm cho chính mình (kiểm tra chuông và realtime SSE).
     * POST /api/notifications/test
     */
    @PostMapping("/test")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificationResponse> sendTestNotification(
            @RequestParam(defaultValue = "Chào mừng bạn đến với Hola Vietnamese!") String title,
            @RequestParam(defaultValue = "Hãy duy trì chuỗi học tập hàng ngày để tích lũy thêm XP nhé!") String message) {

        if (!notificationProperties.isTestEndpointEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        User user = notificationService.getCurrentUser();
        NotificationResponse response = notificationService.createNotification(
                user, title, message, "SYSTEM", "/courses"
        );

        return ApiResponse.<NotificationResponse>builder()
                .code(1000)
                .message("Đã gửi thông báo test thành công")
                .result(response)
                .build();
    }

    /**
     * Trích xuất userId từ SecurityContext hoặc từ JWT token query param.
     */
    private Long resolveUserId(String tokenParam) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            return userRepository.findEnabledActiveByUsernameWithRoles(username)
                    .map(User::getId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        }

        if (tokenParam != null && !tokenParam.isBlank()) {
            try {
                String cleanToken = tokenParam.trim();
                if (cleanToken.startsWith("Bearer ")) {
                    cleanToken = cleanToken.substring(7).trim();
                }
                Jwt jwt = jwtDecoder.decode(cleanToken);
                String username = jwt.getSubject();
                return userRepository.findEnabledActiveByUsernameWithRoles(username)
                        .map(User::getId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
            } catch (Exception e) {
                log.warn("Không thể giải mã token query param cho SSE stream: {}", e.getMessage());
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
