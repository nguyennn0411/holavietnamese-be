package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.dto.PageResponse;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.dto.response.NotificationResponse;
import com.sep490.backend.modules.auth.entity.Notification;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.NotificationRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService implements NotificationPort {

    private static final String DEFAULT_TYPE = NotificationType.SYSTEM.name();

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationStreamHub streamHub;
    private final NotificationProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications() {
        User user = getCurrentUser();
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUserNotifications(int page, int size, Boolean isRead) {
        User user = getCurrentUser();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), Math.max(1, properties.getMaxPageSize()));
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        Page<Notification> notifications = isRead == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), pageable)
                : notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDescIdDesc(
                        user.getId(), isRead, pageable);

        return PageResponse.<NotificationResponse>builder()
                .content(notifications.getContent().stream().map(this::mapToResponse).toList())
                .page(safePage)
                .size(safeSize)
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        return notificationRepository.countByUserIdAndIsReadFalse(getCurrentUser().getId());
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        User user = getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> notFound());

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return;
        }

        notification.setIsRead(true);
        notificationRepository.saveAndFlush(notification);
        long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        afterCommit(() -> streamHub.pushRead(user.getId(), notificationId, unreadCount));
    }

    @Transactional
    public void markAllAsRead() {
        User user = getCurrentUser();
        int changed = notificationRepository.markAllAsReadByUserId(user.getId());
        if (changed > 0) {
            afterCommit(() -> streamHub.pushReadAll(user.getId()));
        }
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        User user = getCurrentUser();
        if (notificationRepository.deleteByIdAndUserId(notificationId, user.getId()) == 0) {
            throw notFound();
        }
        long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        afterCommit(() -> streamHub.pushDeleted(user.getId(), notificationId, unreadCount));
    }

    /** Direct creation API retained for administrative/dev use. */
    @Transactional
    public NotificationResponse createNotification(
            User user, String title, String message, String type, String linkUrl) {
        return persist(user, NotificationMessage.standard(
                title, message, parseType(type), linkUrl), null);
    }

    @Transactional
    public NotificationResponse createNotification(
            Long userId, String title, String message, String type, String linkUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return createNotification(user, title, message, type, linkUrl);
    }

    /** Broadcasts a non-mandatory message to active users who have notifications enabled. */
    @Transactional
    public int createNotificationForAll(
            String title, String message, String type, String linkUrl) {
        return broadcast(NotificationMessage.standard(title, message, parseType(type), linkUrl));
    }

    @Override
    @Transactional
    public boolean send(Long userId, NotificationMessage message) {
        return sendOnce(userId, message, null);
    }

    @Override
    @Transactional
    public boolean sendOnce(Long userId, NotificationMessage message, String eventKey) {
        if (!properties.isEnabled()) {
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.debug("Bỏ qua thông báo vì user {} không còn tồn tại", userId);
            return false;
        }
        if (!isEligible(user, message)) {
            return false;
        }

        String normalizedEventKey = normalizeEventKey(eventKey);
        if (normalizedEventKey != null
                && notificationRepository.findByUserIdAndEventKey(userId, normalizedEventKey).isPresent()) {
            return false;
        }

        persist(user, message, normalizedEventKey);
        return true;
    }

    @Override
    @Transactional
    public int sendToRole(String role, NotificationMessage message) {
        if (!properties.isEnabled() || role == null || role.isBlank()) {
            return 0;
        }
        int sent = 0;
        for (User user : userRepository.findActiveUsersByRole(role.trim().toUpperCase(Locale.ROOT))) {
            if (isEligible(user, message)) {
                persist(user, message, null);
                sent++;
            }
        }
        return sent;
    }

    @Override
    @Transactional
    public int broadcast(NotificationMessage message) {
        if (!properties.isEnabled()) {
            return 0;
        }
        int sent = 0;
        for (User user : userRepository.findAllActiveUsers()) {
            if (isEligible(user, message)) {
                persist(user, message, null);
                sent++;
            }
        }
        return sent;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findActiveByUsernameWithRoles(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private NotificationResponse persist(
            User user, NotificationMessage message, String eventKey) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(requireLength(message.title(), 200, "title"));
        notification.setMessage(message.message());
        notification.setType(message.type().name());
        notification.setLinkUrl(requireLength(message.linkUrl(), 500, "linkUrl"));
        notification.setEventKey(requireLength(eventKey, 200, "eventKey"));
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);
        afterCommit(() -> streamHub.push(user.getId(), response));
        return response;
    }

    private boolean isEligible(User user, NotificationMessage message) {
        if (Boolean.TRUE.equals(user.getIsRemoved())) {
            return false;
        }
        if (message.mandatory()) {
            return true;
        }
        return user.isEnabled()
                && "ACTIVE".equalsIgnoreCase(user.getStatus())
                && !Boolean.FALSE.equals(user.getNotificationsEnabled());
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safelyRun(action);
                }
            });
        } else {
            safelyRun(action);
        }
    }

    private void safelyRun(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("Không thể đẩy sự kiện thông báo realtime: {}", exception.getMessage());
        }
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

    private NotificationType parseType(String type) {
        if (type == null || type.isBlank()) {
            return NotificationType.SYSTEM;
        }
        try {
            return NotificationType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            log.debug("Loại thông báo '{}' không hợp lệ; dùng {}", type, DEFAULT_TYPE);
            return NotificationType.SYSTEM;
        }
    }

    private String normalizeEventKey(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Thông báo không tồn tại hoặc không thuộc về bạn");
    }
}
