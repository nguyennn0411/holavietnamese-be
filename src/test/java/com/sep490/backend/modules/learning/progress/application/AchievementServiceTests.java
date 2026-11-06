package com.sep490.backend.modules.learning.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.BadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.UserBadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.Badge;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.UserBadge;
import com.sep490.backend.modules.learning.progress.port.AiProgressReader;
import com.sep490.backend.modules.learning.progress.port.VocabularyProgressReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AchievementServiceTests {
  private BadgeRepository badges;
  private UserBadgeRepository userBadges;
  private UserRepository users;
  private NotificationPort notifications;
  private AchievementService service;

  @BeforeEach
  void setUp() {
    badges = mock(BadgeRepository.class);
    userBadges = mock(UserBadgeRepository.class);
    users = mock(UserRepository.class);
    notifications = mock(NotificationPort.class);
    service = new AchievementService(
        badges, userBadges, users, notifications, mock(EnrollmentRepository.class),
        mock(VocabularyProgressReader.class), mock(AiProgressReader.class), mock(JdbcTemplate.class));
  }

  @Test
  void invalidAwardRequestDoesNothing() {
    assertThat(service.checkAndAwardBadges(null, "LESSON_COMPLETED", 1)).isEmpty();
    assertThat(service.checkAndAwardBadges(user(), " ", 1)).isEmpty();
    verifyNoInteractions(badges, userBadges, users, notifications);
  }

  @Test
  void legacyConditionAliasAwardsBadgeXpAndNotification() {
    Badge badge = badge(10L, "FIRST_LESSON", "LESSON_COMPLETED", 1, 50, "ACTIVE");
    when(badges.findAll()).thenReturn(List.of(badge));
    when(userBadges.findByUserId(3L)).thenReturn(List.of());
    User user = user();

    var awarded = service.checkAndAwardBadges(user, "LESSON_COUNT", 1);

    assertThat(awarded).containsExactly(badge);
    assertThat(user.getTotalXp()).isEqualTo(60);
    verify(userBadges).save(argThat(value -> value.getBadge() == badge && value.getUser() == user));
    verify(users).save(user);
    verify(notifications).sendOnce(eq(3L), any(), eq("BADGE_UNLOCKED:3:FIRST_LESSON"));
  }

  @Test
  void alreadyUnlockedOrInactiveBadgeIsNotAwardedAgain() {
    Badge active = badge(10L, "FIRST", "LESSON_COMPLETED", 1, 50, "ACTIVE");
    Badge inactive = badge(11L, "OLD", "LESSON_COMPLETED", 1, 50, "INACTIVE");
    UserBadge unlocked = new UserBadge();
    unlocked.setBadge(active);
    unlocked.setUser(user());
    when(badges.findAll()).thenReturn(List.of(active, inactive));
    when(userBadges.findByUserId(3L)).thenReturn(List.of(unlocked));

    assertThat(service.checkAndAwardBadges(user(), "LESSON_COMPLETED", 10)).isEmpty();
    verify(userBadges, never()).save(any());
    verifyNoInteractions(notifications);
  }

  private static User user() {
    User user = new User();
    user.setId(3L);
    user.setTotalXp(10);
    return user;
  }

  private static Badge badge(long id, String code, String type, int value, int xp, String status) {
    Badge badge = new Badge();
    badge.setId(id);
    badge.setCode(code);
    badge.setName(code);
    badge.setDescription("Description");
    badge.setConditionType(type);
    badge.setConditionValue(value);
    badge.setXpReward(xp);
    badge.setStatus(status);
    return badge;
  }
}
