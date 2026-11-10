package com.sep490.backend.modules.learning.progress.application;

import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.BadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.UserBadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.Badge;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.UserBadge;
import com.sep490.backend.modules.learning.progress.port.VocabularyProgressReader;
import com.sep490.backend.modules.learning.progress.port.AiProgressReader;
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
    private final AiProgressReader aiProgressReader;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<BadgeResponse> getUserBadges() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ensureDefaultBadges();
        List<Badge> allBadges = badgeRepository.findAll().stream().filter(b -> b.getStatus() == null || "ACTIVE".equalsIgnoreCase(b.getStatus())).toList();
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
            completedRoleplay = (int) aiProgressReader.countCompletedRoleplays(user.getId());
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

            int currentVal = switch (normalizeConditionType(badge.getConditionType())) {
                case "STREAK_DAYS" -> finalStreak;
                case "LESSON_COMPLETED" -> finalLessons;
                case "VOCAB_LEARNED" -> finalVocab;
                case "QUIZ_PERFECT" -> finalQuizzes;
                case "AI_ROLEPLAY_COMPLETED" -> finalRoleplay;
                default -> 0;
            };

            if (!isUnlocked && currentVal >= conditionVal && ("ACTIVE".equalsIgnoreCase(badge.getStatus()) || badge.getStatus() == null)) {
                try {
                    UserBadge ubNew = new UserBadge();
                    ubNew.setUser(user);
                    ubNew.setBadge(badge);
                    ubNew.setUnlockedAt(LocalDateTime.now());
                    ubNew.setBadgeLevel(1);
                    userBadgeRepository.save(ubNew);
                    userBadgeMap.put(badge.getId(), ubNew);
                    isUnlocked = true;
                    ub = ubNew;

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
                                    "/achievements?badgeId=" + badge.getId()),
                            eventKey);
                } catch (Exception ex) {
                    log.warn("Could not auto-award badge {}: {}", badge.getCode(), ex.getMessage());
                }
            }

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
                    .status(badge.getStatus() != null ? badge.getStatus() : "ACTIVE")
                    .active("ACTIVE".equalsIgnoreCase(badge.getStatus()))
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

        List<Badge> activeBadges = badgeRepository.findAll().stream().filter(b -> b.getStatus() == null || "ACTIVE".equalsIgnoreCase(b.getStatus()))
                .filter(b -> normalizeConditionType(conditionType).equalsIgnoreCase(normalizeConditionType(b.getConditionType())))
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
                            "/achievements?badgeId=" + badge.getId()),
                    eventKey);

            log.info("Đã mở khóa huy hiệu [{}] cho người dùng ID: {}", badge.getCode(), user.getId());
        }

        return newlyAwarded;
    }

    @Transactional
    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            ensureDefaultBadges();
        } catch (Exception e) {
            log.warn("Could not seed default badges on startup: {}", e.getMessage());
        }
    }

    @Transactional
    public void ensureDefaultBadges() {
        createBadgeIfAbsent("FIRST_LESSON", "Bước Đầu Tiên", "Hoàn thành bài học đầu tiên của bạn", "/badges/first_lesson.png", "LESSON", "LESSON_COMPLETED", 1, 50);
        createBadgeIfAbsent("LESSON_5", "Người Chăm Học", "Hoàn thành 5 bài học tiếng Việt", "/badges/lesson_5.png", "LESSON", "LESSON_COMPLETED", 5, 100);
        createBadgeIfAbsent("LESSON_20", "Hành Trình Bền Bỉ", "Hoàn thành 20 bài học tiếng Việt", "/badges/lesson_20.png", "LESSON", "LESSON_COMPLETED", 20, 250);

        createBadgeIfAbsent("STREAK_3_DAYS", "Khởi Động Giữ Lửa", "Duy trì chuỗi học 3 ngày liên tục", "/badges/streak_3.png", "STREAK", "STREAK_DAYS", 3, 30);
        createBadgeIfAbsent("STREAK_7_DAYS", "Thói Quen Vàng", "Duy trì chuỗi 7 ngày học liên tục", "/badges/streak_7.png", "STREAK", "STREAK_DAYS", 7, 100);
        createBadgeIfAbsent("STREAK_14_DAYS", "Kiên Trì Bền Bỉ", "Duy trì chuỗi 14 ngày học liên tục", "/badges/streak_14.png", "STREAK", "STREAK_DAYS", 14, 150);
        createBadgeIfAbsent("STREAK_30_DAYS", "Kẻ Giữ Lửa Bất Diệt", "Duy trì chuỗi 30 ngày học liên tục", "/badges/streak_30.png", "STREAK", "STREAK_DAYS", 30, 300);

        createBadgeIfAbsent("VOCAB_10", "Vườn Từ Đầu Tiên", "Lưu và học 10 từ vựng vào sổ tay", "/badges/vocab_10.png", "VOCABULARY", "VOCAB_LEARNED", 10, 50);
        createBadgeIfAbsent("VOCAB_50", "Nhà Sưu Tầm Từ Vựng", "Học và lưu 50 từ vựng vào sổ tay", "/badges/vocab_50.png", "VOCABULARY", "VOCAB_LEARNED", 50, 150);
        createBadgeIfAbsent("VOCAB_100", "Từ Điển Sống", "Học và lưu 100 từ vựng vào sổ tay", "/badges/vocab_100.png", "VOCABULARY", "VOCAB_LEARNED", 100, 300);

        createBadgeIfAbsent("QUIZ_PERFECT_1", "Mắt Đại Bàng", "Đạt điểm 100% tuyệt đối trong 1 bài kiểm tra", "/badges/quiz_perfect_1.png", "QUIZ", "QUIZ_PERFECT", 1, 50);
        createBadgeIfAbsent("QUIZ_MASTER", "Bậc Thầy Quiz", "Hoàn thành 10 bài kiểm tra đạt điểm tối đa", "/badges/quiz_master.png", "QUIZ", "QUIZ_PERFECT", 10, 200);

        createBadgeIfAbsent("AI_ROLEPLAY_1", "Lời Chào Đầu Tiên", "Hoàn thành phiên luyện nói hội thoại AI đầu tiên", "/badges/ai_roleplay_1.png", "AI_TUTOR", "AI_ROLEPLAY_COMPLETED", 1, 50);
        createBadgeIfAbsent("AI_PIONEER", "Nhà Giao Tiếp AI", "Hoàn thành 3 tình huống hội thoại giao tiếp cùng AI", "/badges/ai_pioneer.png", "AI_TUTOR", "AI_ROLEPLAY_COMPLETED", 3, 100);
        createBadgeIfAbsent("AI_ROLEPLAY_5", "Tự Tin Giao Tiếp", "Hoàn thành 5 tình huống hội thoại giao tiếp cùng AI", "/badges/ai_roleplay_5.png", "AI_TUTOR", "AI_ROLEPLAY_COMPLETED", 5, 150);
    }

    private void createBadgeIfAbsent(String code, String name, String desc, String iconUrl, String category, String conditionType, int conditionValue, int xpReward) {
        if (badgeRepository.findByCode(code).isEmpty()) {
            Badge badge = new Badge();
            badge.setCode(code);
            badge.setName(name);
            badge.setDescription(desc);
            badge.setIconUrl(iconUrl);
            badge.setCategory(category);
            badge.setConditionType(conditionType);
            badge.setConditionValue(conditionValue);
            badge.setXpReward(xpReward);
            badge.setStatus("ACTIVE");
            badgeRepository.save(badge);
        }
    }

    public Badge createBadge(Badge badge) {
        return badgeRepository.save(badge);
    }
    private String normalizeConditionType(String type) {
        if (type == null) return "";
        return switch (type.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "LESSON_COUNT" -> "LESSON_COMPLETED";
            case "VOCAB_COUNT" -> "VOCAB_LEARNED";
            case "QUIZ_SCORE" -> "QUIZ_PERFECT";
            case "AI_TUTOR_COUNT" -> "AI_ROLEPLAY_COMPLETED";
            default -> type.trim().toUpperCase(java.util.Locale.ROOT);
        };
    }
}
