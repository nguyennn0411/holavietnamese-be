package com.sep490.backend.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NotificationStreamHubTests {

    private NotificationProperties properties;
    private NotificationStreamHub streamHub;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setRealtimeEnabled(true);
        properties.setMaxConnectionsPerUser(1);
        streamHub = new NotificationStreamHub(properties);
    }

    @Test
    void disabledRealtimeUsesStableNotificationError() {
        properties.setRealtimeEnabled(false);

        assertThatThrownBy(() -> streamHub.subscribe(7L))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.NOTIFICATION_REALTIME_UNAVAILABLE);
                    assertThat(exception.getErrorCode().getCode()).isEqualTo(5002);
                    assertThat(exception.getErrorCode().getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void connectionLimitUsesStableNotificationErrorAndKeepsExistingStream() {
        streamHub.subscribe(9L);

        assertThatThrownBy(() -> streamHub.subscribe(9L))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.NOTIFICATION_CONNECTION_LIMIT_REACHED);
                    assertThat(exception.getErrorCode().getCode()).isEqualTo(5003);
                    assertThat(exception.getErrorCode().getStatusCode())
                            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
        assertThat(streamHub.connectionCount(9L)).isEqualTo(1);
    }

    @Test
    void reservedNotificationErrorsRemainStable() {
        assertThat(ErrorCode.NOTIFICATION_NOT_FOUND.getCode()).isEqualTo(5001);
        assertThat(ErrorCode.NOTIFICATION_NOT_FOUND.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INVALID_NOTIFICATION_REQUEST.getCode()).isEqualTo(5004);
        assertThat(ErrorCode.INVALID_NOTIFICATION_REQUEST.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
