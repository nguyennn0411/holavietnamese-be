package com.sep490.backend.modules.learning.progress.application;

import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.BadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.UserBadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.Badge;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.UserBadge;
import com.sep490.backend.modules.learning.progress.port.VocabularyProgressReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;
    private final NotificationPort notifications;
    private final EnrollmentRepository enrollmentRepository;
    private final VocabularyProgressReader vocabularyProgressReader;
    private final AiSessionRepository aiSessionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<BadgeResponse> getUserBadges() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<Badge> allBadges = badgeRepository.findByStatus("ACTIVE");
        List<UserBadge> unlockedBadges = userBadgeRepository.findByUserId(user.getId());

        Map<Long, UserBadge> userBadgeMap = unlockedBadges.stream()
                .collect(Collectors.toMap(ub -> ub.getBadge().getId(), ub -> ub));

        // Gather metrics for progress calculation
        int streakCount = user.getStreakCount() != null ? user.getStreakCount() : 0;
        int completedLessons = enrollmentRepository != null ? enrollmentRepository.completedLessonsByUser(user.getId()) : 0;
        int learnedVocab = vocabularyProgressReader != null ? vocabularyProgressReader.countByUser(user.getId()) : 0;
        int perfectQuizzes = 0;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(DISTINCT entity_id) FROM learning_events WHERE user_id=? AND event_type='QUIZ_PERFECT_SCORE'",
                    Integer.class, user.getId());
            perfectQuizzes = count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Could not calculate perfect quizzes for user {}: {}", user.getId(), e.getMessage());
        }

        int completedRoleplay = 0;
        try {
            completedRoleplay = (int) aiSessionRepository.countByUserIdAndStatus(user.getId(), "COMPLETED");
        } catch (Exception e) {
            log.warn("Could not calculate completed roleplay for user {}: {}", user.getId(), e.getMessage());
        }

        final int finalStreak = streakCount;
        final int finalLessons = completedLessons;
        final int finalVocab = learnedVocab;
        final int finalQuizzes = perfectQuizzes;
        final int finalRoleplay = completedRoleplay;

        return allBadges.stream().map(badge -> {
            UserBadge ub = userBadgeMap.get(badge.getId());
            boolean isUnlocked = ub != null;
            int conditionVal = badge.getConditionValue() != null ? badge.getConditionValue() : 1;

            int currentVal = switch (badge.getConditionType().toUpperCase()) {
                case "STREAK_DAYS" -> finalStreak;
                case "LESSON_COMPLETED" -> finalLessons;
                case "VOCAB_LEARNED" -> finalVocab;
                case "QUIZ_PERFECT" -> finalQuizzes;
                case "AI_ROLEPLAY_COMPLETED" -> finalRoleplay;
                default -> 0;
            };

            int currentProgress;
            int progressPercent;
            if (isUnlocked) {
                currentProgress = conditionVal;
                progressPercent = 100;
            } else {
                currentProgress = Math.min(conditionVal, Math.max(0, currentVal));
                progressPercent = conditionVal > 0 ? Math.min(99, (currentProgress * 100) / conditionVal) : 0;
            }

            return BadgeResponse.builder()
                    .id(badge.getId())
                    .code(badge.getCode())
                    .name(badge.getName())
                    .description(badge.getDescription())
                    .iconUrl(badge.getIconUrl())
                    .category(badge.getCategory())
                    .conditionType(badge.getConditionType())
                    .conditionValue(conditionVal)
                    .xpReward(badge.getXpReward())
                    .isUnlocked(isUnlocked)
                    .unlockedAt(isUnlocked ? ub.getUnlockedAt() : null)
                    .badgeLevel(isUnlocked ? ub.getBadgeLevel() : 1)
                    .currentProgress(currentProgress)
                    .progressPercent(progressPercent)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public List<Badge> checkAndAwardBadges(User user, String conditionType, int currentValue) {
        if (user == null || conditionType == null || conditionType.isBlank()) {
            return List.of();
        }

        List<Badge> activeBadges = badgeRepository.findByStatus("ACTIVE").stream()
                .filter(b -> conditionType.equalsIgnoreCase(b.getConditionType()))
                .filter(b -> b.getConditionValue() != null && currentValue >= b.getConditionValue())
                .toList();

        if (activeBadges.isEmpty()) {
            return List.of();
        }

        List<UserBadge> existingBadges = userBadgeRepository.findByUserId(user.getId());
        Map<Long, UserBadge> existingBadgeMap = existingBadges.stream()
                .collect(Collectors.toMap(ub -> ub.getBadge().getId(), ub -> ub));

        List<Badge> newlyAwarded = new ArrayList<>();
        for (Badge badge : activeBadges) {
            if (existingBadgeMap.containsKey(badge.getId())) {
                continue;
            }

            UserBadge userBadge = new UserBadge();
            userBadge.setUser(user);
            userBadge.setBadge(badge);
            userBadge.setUnlockedAt(LocalDateTime.now());
            userBadge.setBadgeLevel(1);
            userBadgeRepository.save(userBadge);
            newlyAwarded.add(badge);

            if (badge.getXpReward() != null && badge.getXpReward() > 0) {
                user.setTotalXp((user.getTotalXp() != null ? user.getTotalXp() : 0) + badge.getXpReward());
                userRepository.save(user);
            }

            String eventKey = "BADGE_UNLOCKED:" + user.getId() + ":" + badge.getCode();
            notifications.sendOnce(
                    user.getId(),
                    NotificationMessage.standard(
                            "Mở khóa huy hiệu: " + badge.getName() + " 🏆",
                            "Chúc mừng! Bạn đã đạt huy hiệu \"" + badge.getName() + "\": "
                                     + badge.getDescription() + " (+" + badge.getXpReward() + " XP).",
                            NotificationType.ACHIEVEMENT,
                            "/achievements"),
                    eventKey);

            log.info("Đã mở khóa huy hiệu [{}] cho người dùng ID: {}", badge.getCode(), user.getId());
        }

        return newlyAwarded;
    }

    @Transactional
    public Badge createBadge(Badge badge) {
        return badgeRepository.save(badge);
    }
}
