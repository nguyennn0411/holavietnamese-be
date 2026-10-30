package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.auth.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMaintenanceService {

    private final NotificationRepository repository;
    private final NotificationProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.notification.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredReadNotifications() {
        if (!properties.isEnabled() || properties.getRetentionDays() <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(properties.getRetentionDays());
        long deleted = repository.deleteByIsReadTrueAndCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Đã dọn {} thông báo cũ đã đọc", deleted);
        }
    }
}
