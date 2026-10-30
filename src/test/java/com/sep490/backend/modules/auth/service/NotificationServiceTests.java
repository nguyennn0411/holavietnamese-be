package com.sep490.backend.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.entity.Notification;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.NotificationRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationStreamHub streamHub;

    private NotificationProperties properties;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        service = new NotificationService(
                notificationRepository,
                userRepository,
                streamHub,
                properties,
                Clock.fixed(Instant.parse("2026-10-30T01:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void normalLearningMessageHonorsUserOptOut() {
        User user = user(7L);
        user.setNotificationsEnabled(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        boolean sent = service.send(7L, NotificationMessage.standard(
                "Bài học", "Bạn đã hoàn thành bài học.", NotificationType.LESSON, "/learn/1"));

        assertThat(sent).isFalse();
        verify(notificationRepository, never()).save(any());
        verify(streamHub, never()).push(eq(7L), any());
    }

    @Test
    void mandatorySecurityMessageBypassesPreferenceAndAccountStatus() {
        User user = user(8L);
        user.setNotificationsEnabled(false);
        user.setEnabled(false);
        user.setStatus("DISABLED");
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(99L);
            return notification;
        });

        boolean sent = service.send(8L, NotificationMessage.mandatory(
                "Bảo mật", "Mật khẩu đã thay đổi.", NotificationType.SECURITY, "/settings"));

        assertThat(sent).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("SECURITY");
        assertThat(saved.getValue().getCreatedAt())
                .isEqualTo(LocalDateTime.parse("2026-10-30T01:00:00"));
        verify(streamHub).push(eq(8L), any());
    }

    @Test
    void duplicateOutboxEventIsNotStoredAgain() {
        User user = user(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserIdAndEventKey(9L, "COURSE_COMPLETED:9:3"))
                .thenReturn(Optional.of(new Notification()));

        boolean sent = service.sendOnce(9L, NotificationMessage.standard(
                "Hoàn thành", "Đã hoàn thành khóa học.", NotificationType.COURSE, "/learn/3"),
                "COURSE_COMPLETED:9:3");

        assertThat(sent).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void deletingAnotherUsersNotificationReturnsNotFound() {
        authenticate("learner");
        User user = user(10L);
        when(userRepository.findActiveByUsernameWithRoles("learner")).thenReturn(Optional.of(user));
        when(notificationRepository.deleteByIdAndUserId(123L, 10L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteNotification(123L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEnabled(true);
        user.setStatus("ACTIVE");
        user.setNotificationsEnabled(true);
        user.setIsRemoved(false);
        return user;
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "n/a", java.util.List.of()));
    }
}
