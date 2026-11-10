package com.sep490.backend.modules.auth.dto.request;

import com.sep490.backend.common.notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationBroadcastRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String message,
        NotificationType type,
        @Size(max = 500) String linkUrl) {

    public NotificationBroadcastRequest {
        type = type == null ? NotificationType.SYSTEM : type;
    }
}
