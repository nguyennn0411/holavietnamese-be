package com.sep490.backend.modules.learning.progress.application;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.XpRule;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.XpTransaction;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.XpRuleRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.XpTransactionRepository;
import com.sep490.backend.modules.learning.progress.port.VocabularyProgressReader;


import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.dto.request.OnboardingRequest;
import com.sep490.backend.modules.auth.dto.request.UserSettingsRequest;
import com.sep490.backend.modules.auth.dto.response.UserProgressResponse;
import com.sep490.backend.modules.auth.dto.response.UserResponse;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProgressService {

    private final UserRepository userRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final XpRuleRepository xpRuleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final VocabularyProgressReader vocabularyProgressReader;
    private final NotificationPort notifications;
    private final AchievementService achievementService;

    @Transactional
    public UserResponse completeOnboarding(OnboardingRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setNativeLanguage(request.getNativeLanguage());
        user.setLearningGoal(request.getLearningGoal());
        user.setTargetLevel(request.getTargetLevel());
        if (request.getDailyLearningGoalMinutes() != null) {
            user.setDailyLearningGoalMinutes(request.getDailyLearningGoalMinutes());
        }
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        user.setOnboardingCompleted(true);
        userRepository.save(user);

        // Award initial onboarding XP
        awardXp(user, "DAILY_LOGIN", 50, "Hoàn thành Onboarding khởi đầu");

        notifications.sendOnce(
                user.getId(),
                NotificationMessage.standard(
                        "Hoàn thành thiết lập mục tiêu! 🎯",
                        "Bạn đã hoàn tất bước khởi đầu. Chúc bạn có một hành trình học tiếng Việt thật hiệu quả cùng Hola Vietnamese!",
                        NotificationType.WELCOME,
                        "/learning-path"),
                "ONBOARDING_COMPLETED:" + user.getId());

        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateUserSettings(UserSettingsRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (request.getAudioSpeed() != null) user.setAudioSpeed(request.getAudioSpeed());
        if (request.getPronunciationHintsEnabled() != null) user.setPronunciationHintsEnabled(request.getPronunciationHintsEnabled());
        if (request.getAutoTranslateEnabled() != null) user.setAutoTranslateEnabled(request.getAutoTranslateEnabled());
        if (request.getNotificationsEnabled() != null) user.setNotificationsEnabled(request.getNotificationsEnabled());
        if (request.getDailyLearningGoalMinutes() != null) user.setDailyLearningGoalMinutes(request.getDailyLearningGoalMinutes());

        userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProgressResponse getUserProgress() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return buildUserProgressResponse(user);
    }

    public UserProgressResponse buildUserProgressResponse(User user) {
        List<XpTransaction> transactions = xpTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<UserProgressResponse.XpHistoryItem> recentItems = transactions.stream()
                .limit(10)
                .map(t -> UserProgressResponse.XpHistoryItem.builder()
                        .id(t.getId())
                        .amount(t.getAmount())
                        .eventType(t.getEventType())
                        .description(t.getDescription())
                        .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : "")
                        .build())
                .collect(Collectors.toList());

        boolean checkedInToday = user.getLastActivityDate() != null && LocalDate.now().equals(user.getLastActivityDate());
        int totalXp = user.getTotalXp() != null ? user.getTotalXp() : 0;
        UserLevelCalculator.LevelInfo levelInfo = UserLevelCalculator.calculate(totalXp);

        int goalMinutes = user.getDailyLearningGoalMinutes() != null ? user.getDailyLearningGoalMinutes() : 15;
        int dailyGoalXp = goalMinutes <= 5 ? 10 : (goalMinutes <= 15 ? 30 : 50);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int earnedToday = xpTransactionRepository.sumAmountByUserIdSince(user.getId(), startOfDay);
        int dailyGoalProgressPercent = dailyGoalXp > 0 ? Math.min(100, (earnedToday * 100) / dailyGoalXp) : 100;
        boolean isDailyGoalCompleted = earnedToday >= dailyGoalXp;

        return UserProgressResponse.builder()
                .streakCount(user.getStreakCount() != null ? user.getStreakCount() : 0)
                .totalXp(totalXp)
                .hearts(user.getHearts() != null ? user.getHearts() : 5)
                .hasCheckedInToday(checkedInToday)
                .dailyGoalMinutes(goalMinutes)
                .lastActivityDate(user.getLastActivityDate())
                .completedLessonsCount(enrollmentRepository.completedLessonsByUser(user.getId()))
                .learnedVocabulariesCount(vocabularyProgressReader.countByUser(user.getId()))
                .recentXpTransactions(recentItems)
                // Level info
                .currentLevel(levelInfo.getCurrentLevel())
                .levelTitle(levelInfo.getLevelTitle())
                .levelTitleEn(levelInfo.getLevelTitleEn())
                .currentLevelMinXp(levelInfo.getCurrentLevelMinXp())
                .nextLevelRequiredXp(levelInfo.getNextLevelRequiredXp())
                .levelProgressPercent(levelInfo.getLevelProgressPercent())
                // Streak Freeze & Daily Goal
                .streakFreezeCount(user.getStreakFreezeCount() != null ? user.getStreakFreezeCount() : 1)
                .streakFreezeUsedDate(user.getStreakFreezeUsedDate())
                .dailyGoalXp(dailyGoalXp)
                .xpEarnedToday(earnedToday)
                .dailyGoalProgressPercent(dailyGoalProgressPercent)
                .isDailyGoalCompleted(isDailyGoalCompleted)
                .build();
    }

    @Transactional
    public void awardXp(User user, String eventType, int fallbackAmount, String description) {
        int rewardAmount = fallbackAmount;
        XpRule rule = xpRuleRepository.findByEventType(eventType).orElse(null);
        if (rule != null && Boolean.TRUE.equals(rule.getIsActive())) {
            rewardAmount = rule.getXpReward();
            if (rule.getDailyCap() != null && rule.getDailyCap() > 0) {
                LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
                int earnedToday = xpTransactionRepository.sumAmountByUserIdAndEventTypeSince(user.getId(), eventType, startOfDay);
                if (earnedToday >= rule.getDailyCap()) {
                    return; // Daily cap reached
                }
                rewardAmount = Math.min(rewardAmount, rule.getDailyCap() - earnedToday);
            }
        }

        if (rewardAmount <= 0) return;

        XpTransaction transaction = new XpTransaction();
        transaction.setUser(user);
        transaction.setAmount(rewardAmount);
        transaction.setEventType(eventType);
        transaction.setDescription(description);
        xpTransactionRepository.save(transaction);

        user.setTotalXp((user.getTotalXp() != null ? user.getTotalXp() : 0) + rewardAmount);

        // Update daily streak with Streak Freeze check
        LocalDate today = LocalDate.now();
        int oldStreak = user.getStreakCount() != null ? user.getStreakCount() : 0;
        int newStreak = oldStreak;
        if (user.getLastActivityDate() == null) {
            newStreak = 1;
            user.setStreakCount(1);
            user.setLastActivityDate(today);
        } else if (user.getLastActivityDate().equals(today.minusDays(1))) {
            newStreak = oldStreak + 1;
            user.setStreakCount(newStreak);
            user.setLastActivityDate(today);
        } else if (!user.getLastActivityDate().equals(today)) {
            // Check streak freeze rescue if missed exactly 1 day (today.minusDays(2))
            int freezes = user.getStreakFreezeCount() != null ? user.getStreakFreezeCount() : 0;
            if (user.getLastActivityDate().equals(today.minusDays(2)) && freezes > 0) {
                user.setStreakFreezeCount(freezes - 1);
                user.setStreakFreezeUsedDate(today.minusDays(1));
                newStreak = oldStreak + 1;
                user.setStreakCount(newStreak);
                user.setLastActivityDate(today);

                notifications.sendOnce(
                        user.getId(),
                        NotificationMessage.standard(
                                "Đã kích hoạt Đóng Băng Chuỗi! ❄️",
                                "Bảo bối Đóng Băng Chuỗi đã tự động kích hoạt để bảo vệ chuỗi " + newStreak + " ngày của bạn hôm qua!",
                                NotificationType.STREAK,
                                "/profile"),
                        "STREAK_FREEZE_USED:" + user.getId() + ":" + today);
            } else {
                newStreak = 1;
                user.setStreakCount(1);
                user.setLastActivityDate(today);
            }
        }

        userRepository.save(user);

        // Check daily goal notification if completed
        int goalMinutes = user.getDailyLearningGoalMinutes() != null ? user.getDailyLearningGoalMinutes() : 15;
        int dailyGoalXp = goalMinutes <= 5 ? 10 : (goalMinutes <= 15 ? 30 : 50);
        LocalDateTime startOfDay = today.atStartOfDay();
        int earnedToday = xpTransactionRepository.sumAmountByUserIdSince(user.getId(), startOfDay);
        if (earnedToday >= dailyGoalXp) {
            notifications.sendOnce(
                    user.getId(),
                    NotificationMessage.standard(
                            "Hoàn thành mục tiêu học hôm nay! 🎯",
                            "Xuất sắc! Bạn đã đạt " + earnedToday + "/" + dailyGoalXp + " XP mục tiêu hôm nay. Hãy tiếp tục giữ vững phong độ nhé!",
                            NotificationType.ACHIEVEMENT,
                            "/learning-path"),
                    "DAILY_GOAL_COMPLETED:" + user.getId() + ":" + today);
        }

        // Trigger notifications and badge checks on streak advancement
        if (newStreak > oldStreak) {
            checkStreakNotifications(user, newStreak);
            achievementService.checkAndAwardBadges(user, "STREAK_DAYS", newStreak);
        }
    }

    @Transactional
    public UserProgressResponse equipStreakFreeze() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        int currentFreezes = user.getStreakFreezeCount() != null ? user.getStreakFreezeCount() : 0;
        if (currentFreezes >= 2) {
            throw new AppException(ErrorCode.STREAK_FREEZE_MAX_REACHED);
        }

        int currentXp = user.getTotalXp() != null ? user.getTotalXp() : 0;
        int cost = 100;
        if (currentXp < cost) {
            throw new AppException(ErrorCode.INSUFFICIENT_XP);
        }

        user.setTotalXp(currentXp - cost);
        user.setStreakFreezeCount(currentFreezes + 1);
        userRepository.save(user);

        XpTransaction tx = new XpTransaction();
        tx.setUser(user);
        tx.setAmount(-cost);
        tx.setEventType("STREAK_FREEZE_PURCHASE");
        tx.setDescription("Trang bị 1 Đóng Băng Chuỗi (-100 XP)");
        xpTransactionRepository.save(tx);

        notifications.sendOnce(
                user.getId(),
                NotificationMessage.standard(
                        "Đã trang bị Đóng Băng Chuỗi! ❄️",
                        "Bạn đã trang bị thành công 1 Đóng Băng Chuỗi bảo vệ streak. Hãy an tâm học tập!",
                        NotificationType.STREAK,
                        "/profile"),
                "STREAK_FREEZE_EQUIPPED:" + user.getId() + ":" + System.currentTimeMillis());

        return buildUserProgressResponse(user);
    }

    private void checkStreakNotifications(User user, int streak) {
        if (streak == 1) {
            String eventKey = "STREAK:" + user.getId() + ":1:" + LocalDate.now();
            notifications.sendOnce(
                    user.getId(),
                    NotificationMessage.standard(
                            "Khởi động chuỗi học tập! 🔥",
                            "Bạn đã bắt đầu chuỗi ngày học tập hôm nay. Hãy quay lại vào ngày mai để tiếp tục giữ lửa nhé!",
                            NotificationType.STREAK,
                            "/profile"),
                    eventKey);
        } else if (streak == 3 || streak == 7 || streak == 14 || streak == 30 || streak == 50 || streak == 100 || (streak > 30 && streak % 30 == 0)) {
            String eventKey = "STREAK:" + user.getId() + ":" + streak;
            notifications.sendOnce(
                    user.getId(),
                    NotificationMessage.standard(
                            "Đạt chuỗi " + streak + " ngày học liên tục! 🔥",
                            "Xuất sắc! Bạn đã duy trì chuỗi học tập " + streak + " ngày liên tiếp. Tiếp tục phát huy nhé!",
                            NotificationType.STREAK,
                            "/profile"),
                    eventKey);
        }
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .country(user.getCountry())
                .nativeLanguage(user.getNativeLanguage())
                .learningGoal(user.getLearningGoal())
                .targetLevel(user.getTargetLevel())
                .dailyLearningGoalMinutes(user.getDailyLearningGoalMinutes())
                .audioSpeed(user.getAudioSpeed())
                .pronunciationHintsEnabled(user.getPronunciationHintsEnabled())
                .autoTranslateEnabled(user.getAutoTranslateEnabled())
                .notificationsEnabled(user.getNotificationsEnabled())
                .onboardingCompleted(user.getOnboardingCompleted())
                .emailVerified(user.getEmailVerified())
                .streakCount(user.getStreakCount())
                .totalXp(user.getTotalXp())
                .hearts(user.getHearts() != null ? user.getHearts() : 5)
                .status(user.getStatus())
                .roles(user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet()))
                .build();
    }
}
