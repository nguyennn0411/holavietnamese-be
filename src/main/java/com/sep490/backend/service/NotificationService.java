package com.sep490.backend.service;

import com.sep490.backend.dto.response.NotificationResponse;
import com.sep490.backend.dto.response.PageResponse;
import com.sep490.backend.entity.Notification;
import com.sep490.backend.entity.User;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.NotificationRepository;
import com.sep490.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationStreamHub streamHub;

    /**
     * Lấy toàn bộ thông báo của người dùng hiện tại (danh sách phẳng).
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications() {
        User user = getCurrentUser();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách thông báo có phân trang và tùy chọn lọc theo trạng thái đã đọc.
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUserNotifications(int page, int size, Boolean isRead) {
        User user = getCurrentUser();
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, size));

        Page<Notification> notificationPage;
        if (isRead != null) {
            notificationPage = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(user.getId(), isRead, pageable);
        } else {
            notificationPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }

        List<NotificationResponse> content = notificationPage.getContent()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<NotificationResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .build();
    }

    /**
     * Đếm số lượng thông báo chưa đọc của người dùng hiện tại (cho badge chuông thông báo).
     */
    @Transactional(readOnly = true)
    public long countUnread() {
        User user = getCurrentUser();
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    /**
     * Đánh dấu 1 thông báo là đã đọc.
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        User user = getCurrentUser();

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thông báo không tồn tại hoặc không thuộc về bạn"));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);

            // Bắn event đồng bộ realtime giữa các tab đang mở của user
            streamHub.pushRead(user.getId(), notificationId);
        }
    }

    /**
     * Đánh dấu toàn bộ thông báo của người dùng là đã đọc.
     */
    @Transactional
    public void markAllAsRead() {
        User user = getCurrentUser();
        notificationRepository.markAllAsReadByUserId(user.getId());
    }

    /**
     * Xóa 1 thông báo của người dùng.
     */
    @Transactional
    public void deleteNotification(Long notificationId) {
        User user = getCurrentUser();
        notificationRepository.deleteByIdAndUserId(notificationId, user.getId());
    }

    /**
     * Tạo thông báo mới và gửi realtime tới người dùng (qua SSE Stream).
     */
    @Transactional
    public NotificationResponse createNotification(User user, String title, String message, String type, String linkUrl) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type != null ? type : "SYSTEM");
        notification.setLinkUrl(linkUrl);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        // Bắn thông báo Realtime tới các tab đang kết nối SSE của user
        try {
            streamHub.push(user.getId(), response);
        } catch (Exception e) {
            log.warn("Không thể đẩy SSE notification tới user {}: {}", user.getId(), e.getMessage());
        }

        return response;
    }

    /**
     * Tạo thông báo mới theo userId.
     */
    @Transactional
    public NotificationResponse createNotification(Long userId, String title, String message, String type, String linkUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return createNotification(user, title, message, type, linkUrl);
    }

    /**
     * Gửi thông báo hệ thống tới toàn bộ người dùng active.
     */
    @Transactional
    public void createNotificationForAll(String title, String message, String type, String linkUrl) {
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsRemoved()) && "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .collect(Collectors.toList());

        for (User user : activeUsers) {
            createNotification(user, title, message, type, linkUrl);
        }
    }

    public User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .linkUrl(notification.getLinkUrl())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
