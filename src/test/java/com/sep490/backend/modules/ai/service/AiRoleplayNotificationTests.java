package com.sep490.backend.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.ai.entity.AiSession;
import com.sep490.backend.modules.ai.repository.AiMessageRepository;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.ai.repository.AiSettingRepository;
import com.sep490.backend.modules.ai.repository.AiUsageLogRepository;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.progress.application.AchievementService;
import com.sep490.backend.modules.learning.progress.application.UserProgressService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AiRoleplayNotificationTests {

    @Mock AiScenarioRepository scenarioRepository;
    @Mock AiSessionRepository sessionRepository;
    @Mock AiMessageRepository messageRepository;
    @Mock AiUsageLogRepository usageLogRepository;
    @Mock AiSettingRepository settingRepository;
    @Mock UserRepository userRepository;
    @Mock AiTutorService aiTutorService;
    @Mock AiProviderService aiProviderService;
    @Mock ObjectMapper objectMapper;
    @Mock AchievementService achievementService;
    @Mock UserProgressService userProgressService;
    @Mock NotificationPort notifications;

    @InjectMocks AiRoleplayService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void finishingRoleplaySendsOneIdempotentSummaryWithScoreAndDeepLink() {
        User user = new User();
        user.setId(7L);
        AiScenario scenario = AiScenario.builder()
                .id("goi-mon")
                .title("Gọi món tại nhà hàng")
                .completionsCount(0)
                .avgScore(0)
                .build();
        AiSession session = AiSession.builder()
                .id("roleplay-goi-mon-abc123")
                .user(user)
                .mode("roleplay")
                .title(scenario.getTitle())
                .scenario(scenario)
                .build();

        LearnerPrincipal principal = new LearnerPrincipal(7L, "learner@test.local", "", true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", principal.getAuthorities()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(sessionRepository.findByIdAndUserId(session.getId(), 7L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of());
        when(sessionRepository.countByUserIdAndStatus(7L, "COMPLETED")).thenReturn(1L);

        service.finishRoleplay(session.getId(), 92);

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications).sendOnce(
                eq(7L), message.capture(), eq("AI_ROLEPLAY_COMPLETED:7:" + session.getId()));
        assertThat(message.getValue().type()).isEqualTo(NotificationType.SYSTEM);
        assertThat(message.getValue().message()).contains("Gọi món tại nhà hàng", "92/100");
        assertThat(message.getValue().linkUrl())
                .isEqualTo("/ai/roleplay/sessions/" + session.getId());
    }
}
