package com.sep490.backend.common.notification;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private boolean enabled = true;
    private boolean realtimeEnabled = true;
    private boolean testEndpointEnabled = false;
    private int maxPageSize = 100;
    private int maxConnectionsPerUser = 5;
    private Duration streamTimeout = Duration.ofMinutes(30);
    private int retentionDays = 90;
    private int cleanupBatchSize = 500;
    private int learningEventBatchSize = 50;
    private boolean emailEnabled = true;
    private boolean learningReminderEnabled = true;
    private String learningReminderCron = "0 0 19 * * *";
    private String learningReminderZone = "Asia/Ho_Chi_Minh";
    private int learningReminderInactiveHours = 24;
    private int learningReminderBatchSize = 200;
}
