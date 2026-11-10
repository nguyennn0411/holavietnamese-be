package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.auth.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMaintenanceService {

    private final NotificationRepository repository;
    private final NotificationProperties properties;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "${app.notification.cleanup-cron:0 0 3 * * *}")
    public void deleteExpiredReadNotifications() {
        if (!properties.isEnabled() || properties.getRetentionDays() <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(properties.getRetentionDays());
        int batchSize = Math.max(1, Math.min(5_000, properties.getCleanupBatchSize()));
        TransactionTemplate batchTransaction = new TransactionTemplate(transactionManager);
        batchTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        long totalDeleted = 0;
        while (true) {
            Integer deleted = batchTransaction.execute(status -> deleteBatch(cutoff, batchSize));
            if (deleted == null || deleted == 0) {
                break;
            }
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("Đã dọn {} thông báo cũ đã đọc", totalDeleted);
        }
    }

    private int deleteBatch(LocalDateTime cutoff, int batchSize) {
        var ids = repository.findExpiredReadIds(cutoff, PageRequest.of(0, batchSize));
        return ids.isEmpty() ? 0 : repository.deleteAllByIdInBatch(ids);
    }
}
