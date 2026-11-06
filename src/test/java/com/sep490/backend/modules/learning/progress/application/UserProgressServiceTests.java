package com.sep490.backend.modules.learning.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.XpRuleRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.XpTransactionRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.XpRule;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.XpTransaction;
import com.sep490.backend.modules.learning.progress.port.VocabularyProgressReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProgressServiceTests {
  private UserRepository users;
  private XpTransactionRepository transactions;
  private XpRuleRepository rules;
  private EnrollmentRepository enrollments;
  private VocabularyProgressReader vocabulary;
  private NotificationPort notifications;
  private AchievementService achievements;
  private UserProgressService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    transactions = mock(XpTransactionRepository.class);
    rules = mock(XpRuleRepository.class);
    enrollments = mock(EnrollmentRepository.class);
    vocabulary = mock(VocabularyProgressReader.class);
    notifications = mock(NotificationPort.class);
    achievements = mock(AchievementService.class);
    service = new UserProgressService(
        users, transactions, rules, enrollments, vocabulary, notifications, achievements);
  }

  @Test
  void disabledXpRuleAwardsNothing() {
    XpRule rule = rule(false, 20, 100);
    when(rules.findByEventType("LESSON_COMPLETED")).thenReturn(Optional.of(rule));
    User user = user();

    service.awardXp(user, "LESSON_COMPLETED", 10, "Lesson");

    assertThat(user.getTotalXp()).isEqualTo(0);
    verifyNoInteractions(transactions, users, achievements);
  }

  @Test
  void dailyCapClampsRewardAndStartsStreak() {
    XpRule rule = rule(true, 20, 25);
    when(rules.findByEventType("LESSON_COMPLETED")).thenReturn(Optional.of(rule));
    when(transactions.sumAmountByUserIdAndEventTypeSince(eq(3L), eq("LESSON_COMPLETED"), any()))
        .thenReturn(20);
    when(transactions.sumAmountByUserIdSince(eq(3L), any())).thenReturn(5);
    User user = user();

    service.awardXp(user, "LESSON_COMPLETED", 10, "Lesson");

    assertThat(user.getTotalXp()).isEqualTo(5);
    assertThat(user.getStreakCount()).isEqualTo(1);
    assertThat(user.getLastActivityDate()).isEqualTo(LocalDate.now());
    verify(transactions).save(argThat(tx -> tx.getAmount() == 5));
    verify(achievements).checkAndAwardBadges(user, "STREAK_DAYS", 1);
  }

  @Test
  void progressResponseUsesDefaultsAndCapsDailyGoalAtOneHundredPercent() {
    User user = user();
    user.setTotalXp(175);
    user.setDailyLearningGoalMinutes(5);
    user.setLastActivityDate(LocalDate.now());
    when(transactions.findByUserIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
    when(transactions.sumAmountByUserIdSince(eq(3L), any(LocalDateTime.class))).thenReturn(50);
    when(enrollments.completedLessonsByUser(3L)).thenReturn(4);
    when(vocabulary.countByUser(3L)).thenReturn(12);

    var result = service.buildUserProgressResponse(user);

    assertThat(result.getCurrentLevel()).isEqualTo(2);
    assertThat(result.getCompletedLessonsCount()).isEqualTo(4);
    assertThat(result.getLearnedVocabulariesCount()).isEqualTo(12);
    assertThat(result.getDailyGoalProgressPercent()).isEqualTo(100);
    assertThat(result.getIsDailyGoalCompleted()).isTrue();
  }

  private static XpRule rule(boolean active, int reward, int cap) {
    XpRule rule = new XpRule();
    rule.setIsActive(active);
    rule.setXpReward(reward);
    rule.setDailyCap(cap);
    return rule;
  }

  private static User user() {
    User user = new User();
    user.setId(3L);
    user.setTotalXp(0);
    user.setStreakCount(0);
    user.setStreakFreezeCount(1);
    user.setDailyLearningGoalMinutes(15);
    return user;
  }
}
