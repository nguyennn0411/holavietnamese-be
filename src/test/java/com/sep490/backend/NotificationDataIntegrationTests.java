package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.service.NotificationMaintenanceService;
import com.sep490.backend.modules.auth.service.NotificationService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class NotificationDataIntegrationTests {

    @Autowired ContentStore db;
    @Autowired NotificationService notificationService;
    @Autowired NotificationMaintenanceService maintenanceService;
    @Autowired NotificationProperties properties;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;

    @Test
    void concurrentSendOnceStoresExactlyOneNotificationWithoutFailingCaller() throws Exception {
        long userId = createUser();
        String eventKey = "CONCURRENT_NOTIFICATION:" + userId + ":" + UUID.randomUUID();
        NotificationMessage message = NotificationMessage.standard(
                "Thông báo đồng thời",
                "Mỗi sự kiện chỉ được lưu một lần.",
                NotificationType.SYSTEM,
                "/profile");

        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> action = () -> {
                ready.countDown();
                start.await();
                return notificationService.sendOnce(userId, message, eventKey);
            };

            List<Boolean> outcomes;
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(action);
                var second = executor.submit(action);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                outcomes = List.of(
                        first.get(20, TimeUnit.SECONDS),
                        second.get(20, TimeUnit.SECONDS));
            }

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            assertThat(db.count(
                    "SELECT COUNT(*) FROM notifications WHERE user_id=? AND event_key=?",
                    userId,
                    eventKey)).isEqualTo(1);
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void cleanupDeletesExpiredReadNotificationsAcrossBatchesButKeepsUnread() {
        long userId = createUser();
        int previousBatchSize = properties.getCleanupBatchSize();
        int previousRetentionDays = properties.getRetentionDays();
        boolean previouslyEnabled = properties.isEnabled();

        try {
            properties.setEnabled(true);
            properties.setRetentionDays(90);
            properties.setCleanupBatchSize(2);
            inTransaction(() -> {
                for (int index = 0; index < 5; index++) {
                    insertNotification(userId, "expired-read-" + index, true, LocalDateTime.of(2000, 1, 1, 0, index));
                }
                insertNotification(userId, "expired-unread", false, LocalDateTime.of(2000, 1, 1, 1, 0));
            });

            maintenanceService.deleteExpiredReadNotifications();

            assertThat(db.count(
                    "SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=true",
                    userId)).isZero();
            assertThat(db.count(
                    "SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=false",
                    userId)).isEqualTo(1);
        } finally {
            properties.setCleanupBatchSize(previousBatchSize);
            properties.setRetentionDays(previousRetentionDays);
            properties.setEnabled(previouslyEnabled);
            deleteUser(userId);
        }
    }

    @Test
    void migrationEnforcesRequiredStateColumnsAndCreatesCleanupIndex() throws Exception {
        long userId = createUser();
        try {
            assertThatThrownBy(() -> inTransaction(() -> db.update(
                    "INSERT INTO notifications(user_id,title,message,is_read,created_at)"
                            + " VALUES(?, 'null-read', 'invalid', NULL, CURRENT_TIMESTAMP)",
                    userId)))
                    .isInstanceOf(DataAccessException.class);

            assertThatThrownBy(() -> inTransaction(() -> db.update(
                    "INSERT INTO notifications(user_id,title,message,is_read,created_at)"
                            + " VALUES(?, 'null-created', 'invalid', FALSE, NULL)",
                    userId)))
                    .isInstanceOf(DataAccessException.class);

            assertThat(hasIndex("idx_notification_cleanup")).isTrue();
        } finally {
            deleteUser(userId);
        }
    }

    private long createUser() {
        return Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status -> {
            String email = "notification-data-" + UUID.randomUUID() + "@test.local";
            return db.insert(
                    "INSERT INTO users(email,password_hash,enabled,status,notifications_enabled)"
                            + " VALUES(?, 'test-only', true, 'ACTIVE', true)",
                    email);
        }));
    }

    private void insertNotification(
            long userId, String title, boolean isRead, LocalDateTime createdAt) {
        db.insert(
                "INSERT INTO notifications(user_id,title,message,type,is_read,created_at)"
                        + " VALUES(?,?,?,'SYSTEM',?,?)",
                userId,
                title,
                "test",
                isRead,
                createdAt);
    }

    private void deleteUser(long userId) {
        inTransaction(() -> {
            db.update("DELETE FROM notifications WHERE user_id=?", userId);
            db.update("DELETE FROM users WHERE id=?", userId);
        });
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private boolean hasIndex(String expectedName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String[] schemas = {connection.getSchema(), null};
            for (String tableName : List.of("notifications", "NOTIFICATIONS")) {
                for (String schema : schemas) {
                    try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                            connection.getCatalog(), schema, tableName, false, false)) {
                        while (indexes.next()) {
                            String indexName = indexes.getString("INDEX_NAME");
                            if (expectedName.equalsIgnoreCase(indexName)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }
}
