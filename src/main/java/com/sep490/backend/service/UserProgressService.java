package com.sep490.backend.service;

import com.sep490.backend.dto.request.OnboardingRequest;
import com.sep490.backend.dto.request.UserSettingsRequest;
import com.sep490.backend.dto.response.UserProgressResponse;
import com.sep490.backend.dto.response.UserResponse;
import com.sep490.backend.entity.User;
import com.sep490.backend.entity.XpRule;
import com.sep490.backend.entity.XpTransaction;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.EnrollmentRepository;
import com.sep490.backend.repository.UserRepository;
import com.sep490.backend.repository.VocabularyRepository;
import com.sep490.backend.repository.XpRuleRepository;
import com.sep490.backend.repository.XpTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProgressService {

    private final UserRepository userRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final XpRuleRepository xpRuleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final VocabularyRepository vocabularyRepository;

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

        return UserProgressResponse.builder()
                .streakCount(user.getStreakCount() != null ? user.getStreakCount() : 0)
                .totalXp(user.getTotalXp() != null ? user.getTotalXp() : 0)
                .dailyGoalMinutes(user.getDailyLearningGoalMinutes() != null ? user.getDailyLearningGoalMinutes() : 15)
                .lastActivityDate(user.getLastActivityDate())
                .completedLessonsCount(enrollmentRepository.completedLessonsByUser(user.getId()))
                .learnedVocabulariesCount(vocabularyRepository.countByUser(user.getId()))
                .recentXpTransactions(recentItems)
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

        // Update daily streak
        LocalDate today = LocalDate.now();
        if (user.getLastActivityDate() == null) {
            user.setStreakCount(1);
            user.setLastActivityDate(today);
        } else if (user.getLastActivityDate().equals(today.minusDays(1))) {
            user.setStreakCount(user.getStreakCount() + 1);
            user.setLastActivityDate(today);
        } else if (!user.getLastActivityDate().equals(today)) {
            user.setStreakCount(1);
            user.setLastActivityDate(today);
        }

        userRepository.save(user);
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
                .status(user.getStatus())
                .roles(user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet()))
                .build();
    }
}
