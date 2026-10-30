package com.sep490.backend.modules.learning.progress.application;

import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningNotificationProcessor {

    static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "COURSE_ENROLLED",
            "COURSE_COMPLETED",
            "LESSON_COMPLETED",
            "QUIZ_PASSED",
            "QUIZ_PERFECT_SCORE");

    private final ContentStore db;
    private final NotificationPort notifications;
    private final NotificationProperties properties;
    private final Clock clock;
    private final AchievementService achievementService;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String eventKey) {
        if (!properties.isEnabled()) {
            return;
        }

        var rows = db.rows(
                "SELECT event_key,event_type,user_id,entity_id FROM learning_events"
                        + " WHERE event_key=? AND delivered_at IS NULL FOR UPDATE",
                eventKey);
        if (rows.isEmpty()) {
            return;
        }

        Map<String, Object> event = rows.getFirst();
        String eventType = String.valueOf(event.get("eventType"));
        if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
            return;
        }

        long userId = ContentStore.id(event, "userId");

        notificationFor(event).ifPresent(message -> notifications.sendOnce(
                userId, message, eventKey));

        // Evaluate achievement badges linked to learning events
        evaluateBadgesForEvent(eventType, userId);

        db.update(
                "UPDATE learning_events SET delivered_at=? WHERE event_key=? AND delivered_at IS NULL",
                Timestamp.from(clock.instant()),
                eventKey);
    }

    private void evaluateBadgesForEvent(String eventType, long userId) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                if ("LESSON_COMPLETED".equals(eventType)) {
                    int completedLessons = (int) db.count(
                            "SELECT count(DISTINCT entity_id) FROM learning_events WHERE user_id=? AND event_type='LESSON_COMPLETED'",
                            userId);
                    achievementService.checkAndAwardBadges(user, "LESSON_COMPLETED", completedLessons);
                } else if ("QUIZ_PASSED".equals(eventType) || "QUIZ_PERFECT_SCORE".equals(eventType)) {
                    int perfectQuizzes = (int) db.count(
                            "SELECT count(DISTINCT entity_id) FROM learning_events WHERE user_id=? AND event_type='QUIZ_PERFECT_SCORE'",
                            userId);
                    achievementService.checkAndAwardBadges(user, "QUIZ_PERFECT", perfectQuizzes);
                }
            });
        } catch (Exception e) {
            log.warn("Lỗi khi kiểm tra huy hiệu sau sự kiện {}: {}", eventType, e.getMessage());
        }
    }

    private Optional<NotificationMessage> notificationFor(Map<String, Object> event) {
        String type = String.valueOf(event.get("eventType"));
        long userId = ContentStore.id(event, "userId");
        long entityId = ContentStore.id(event, "entityId");

        return switch (type) {
            case "COURSE_ENROLLED" -> courseMessage(
                    entityId,
                    "Đăng ký khóa học thành công",
                    title -> "Bạn đã đăng ký khóa học \"" + title + "\".",
                    NotificationType.COURSE);
            case "COURSE_COMPLETED" -> courseMessage(
                    entityId,
                    "Chúc mừng bạn đã hoàn thành khóa học!",
                    title -> "Bạn đã hoàn thành khóa học \"" + title + "\".",
                    NotificationType.ACHIEVEMENT);
            case "LESSON_COMPLETED" -> lessonMessage(userId, entityId);
            case "QUIZ_PASSED" -> quizMessage(userId, entityId);
            // The perfect-score event enriches QUIZ_PASSED and must not create a second message.
            case "QUIZ_PERFECT_SCORE" -> Optional.empty();
            default -> {
                log.debug("Không có mẫu thông báo cho learning event {}", type);
                yield Optional.empty();
            }
        };
    }

    private Optional<NotificationMessage> courseMessage(
            long courseId,
            String title,
            java.util.function.Function<String, String> message,
            NotificationType type) {
        var courses = db.rows("SELECT title FROM courses WHERE id=?", courseId);
        if (courses.isEmpty()) {
            return Optional.empty();
        }
        String courseTitle = String.valueOf(courses.getFirst().get("title"));
        return Optional.of(NotificationMessage.standard(
                title, message.apply(courseTitle), type, "/learn/" + courseId));
    }

    private Optional<NotificationMessage> lessonMessage(long userId, long lessonId) {
        var lessons = db.rows(
                "SELECT l.title,l.course_id FROM lessons l WHERE l.id=?",
                lessonId);
        if (lessons.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> lesson = lessons.getFirst();
        long courseId = ContentStore.id(lesson, "courseId");
        if (eventExists("COURSE_COMPLETED", userId, courseId)) {
            return Optional.empty();
        }
        return Optional.of(NotificationMessage.standard(
                "Hoàn thành bài học",
                "Bạn đã hoàn thành bài \"" + lesson.get("title") + "\".",
                NotificationType.LESSON,
                "/learn/" + courseId + "/lesson/" + lessonId));
    }

    private Optional<NotificationMessage> quizMessage(long userId, long quizId) {
        var quizzes = db.rows(
                "SELECT title,course_id,lesson_id FROM quizzes WHERE id=?",
                quizId);
        if (quizzes.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> quiz = quizzes.getFirst();
        Long courseId = nullableId(quiz.get("courseId"));
        Long lessonId = nullableId(quiz.get("lessonId"));
        if (courseId != null && eventExists("COURSE_COMPLETED", userId, courseId)) {
            return Optional.empty();
        }
        if (lessonId != null && eventExists("LESSON_COMPLETED", userId, lessonId)) {
            return Optional.empty();
        }

        boolean perfect = eventExists("QUIZ_PERFECT_SCORE", userId, quizId);
        String message = perfect
                ? "Bạn đã đạt điểm tuyệt đối ở bài kiểm tra \"" + quiz.get("title") + "\"."
                : "Bạn đã vượt qua bài kiểm tra \"" + quiz.get("title") + "\".";
        String link = courseId == null ? "/quizzes/" + quizId : "/learn/" + courseId;
        return Optional.of(NotificationMessage.standard(
                perfect ? "Điểm tuyệt đối!" : "Đã vượt qua bài kiểm tra",
                message,
                perfect ? NotificationType.ACHIEVEMENT : NotificationType.QUIZ,
                link));
    }

    private boolean eventExists(String eventType, long userId, long entityId) {
        return db.count(
                "SELECT count(*) FROM learning_events WHERE event_type=? AND user_id=? AND entity_id=?",
                eventType,
                userId,
                entityId) > 0;
    }

    private Long nullableId(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
