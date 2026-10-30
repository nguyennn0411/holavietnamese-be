package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.NotificationRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.course.application.CourseService;
import com.sep490.backend.modules.learning.path.application.LearningPathService;
import com.sep490.backend.modules.learning.progress.application.AchievementService;
import com.sep490.backend.modules.learning.progress.application.UserProgressService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class NotificationBusinessIntegrationTests {

    @Autowired ContentStore db;
    @Autowired CourseService courses;
    @Autowired NotificationRepository notifications;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserProgressService userProgressService;
    @Autowired AchievementService achievementService;
    @Autowired UserRepository userRepository;
    @Autowired LearningPathService learningPathService;

    @Test
    void committedEnrollmentEventCreatesExactlyOneNotification() {
        Fixture fixture = createFixture(true);
        String eventKey = "COURSE_ENROLLED:" + fixture.userId() + ":" + fixture.courseId();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    courses.enroll(fixture.userId(), fixture.courseId()));

            assertThat(notifications.findByUserIdAndEventKey(fixture.userId(), eventKey))
                    .isPresent()
                    .get()
                    .satisfies(notification -> {
                        assertThat(notification.getType()).isEqualTo("COURSE");
                        assertThat(notification.getLinkUrl()).isEqualTo("/learn/" + fixture.courseId());
                    });
            assertThat(db.count(
                    "SELECT count(*) FROM learning_events WHERE event_key=? AND delivered_at IS NOT NULL",
                    eventKey)).isEqualTo(1);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void optedOutLearnerDoesNotReceiveEnrollmentNotification() {
        Fixture fixture = createFixture(false);
        String eventKey = "COURSE_ENROLLED:" + fixture.userId() + ":" + fixture.courseId();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    courses.enroll(fixture.userId(), fixture.courseId()));

            assertThat(notifications.findByUserIdAndEventKey(fixture.userId(), eventKey)).isEmpty();
            assertThat(db.count(
                    "SELECT count(*) FROM learning_events WHERE event_key=? AND delivered_at IS NOT NULL",
                    eventKey)).isEqualTo(1);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void streakAdvancementCreatesStreakNotification() {
        Fixture fixture = createFixture(true);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                User user = userRepository.findById(fixture.userId()).orElseThrow();
                userProgressService.awardXp(user, "LESSON_COMPLETE", 20, "Hoàn thành bài học kiểm tra chuỗi");
            });

            String streakEventKey = "STREAK:" + fixture.userId() + ":1:" + LocalDate.now();
            assertThat(notifications.findByUserIdAndEventKey(fixture.userId(), streakEventKey))
                    .isPresent()
                    .get()
                    .satisfies(notification -> {
                        assertThat(notification.getType()).isEqualTo("STREAK");
                        assertThat(notification.getLinkUrl()).isEqualTo("/profile");
                    });
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void badgeAwardingSendsAchievementNotification() {
        Fixture fixture = createFixture(true);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                User user = userRepository.findById(fixture.userId()).orElseThrow();
                achievementService.checkAndAwardBadges(user, "LESSON_COMPLETED", 1);
            });

            String badgeEventKey = "BADGE_UNLOCKED:" + fixture.userId() + ":FIRST_LESSON";
            assertThat(notifications.findByUserIdAndEventKey(fixture.userId(), badgeEventKey))
                    .isPresent()
                    .get()
                    .satisfies(notification -> {
                        assertThat(notification.getType()).isEqualTo("ACHIEVEMENT");
                        assertThat(notification.getLinkUrl()).isEqualTo("/achievements");
                    });
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void learningPathApplyCreatesNotification() {
        Fixture fixture = createFixture(true);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    learningPathService.apply(fixture.userId(), List.of(fixture.courseId())));

            var list = notifications.findByUserIdOrderByCreatedAtDescIdDesc(fixture.userId());
            assertThat(list.stream().anyMatch(n ->
                    "COURSE".equals(n.getType()) && "/learning-path".equals(n.getLinkUrl()))).isTrue();
        } finally {
            deleteFixture(fixture);
        }
    }

    private Fixture createFixture(boolean notificationsEnabled) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String email = "notification-" + UUID.randomUUID() + "@test.local";
            long userId = db.insert(
                    "INSERT INTO users(email,password_hash,enabled,status,notifications_enabled)"
                            + " VALUES(?, 'test-only', true, 'ACTIVE', ?)",
                    email,
                    notificationsEnabled);
            long courseId = db.insert(
                    "INSERT INTO courses(title,description,level,estimated_duration,status)"
                            + " VALUES('Notification course','Test','A1',10,'PUBLISHED')");
            return new Fixture(userId, courseId);
        });
    }

    private void deleteFixture(Fixture fixture) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            db.update("DELETE FROM notifications WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM user_badges WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM xp_transactions WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM learning_events WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM enrollments WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM learning_path_courses WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM learning_path_preferences WHERE user_id=?", fixture.userId());
            db.update("DELETE FROM courses WHERE id=?", fixture.courseId());
            db.update("DELETE FROM users WHERE id=?", fixture.userId());
        });
    }

    private record Fixture(long userId, long courseId) {}
}
