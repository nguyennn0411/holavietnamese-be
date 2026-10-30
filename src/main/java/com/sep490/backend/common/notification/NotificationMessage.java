package com.sep490.backend.common.notification;

import java.util.Objects;

/** Channel-neutral notification content. */
public record NotificationMessage(
        String title,
        String message,
        NotificationType type,
        String linkUrl,
        boolean mandatory) {

    public NotificationMessage {
        title = requireText(title, "title");
        message = requireText(message, "message");
        type = Objects.requireNonNullElse(type, NotificationType.SYSTEM);
        linkUrl = normalize(linkUrl);
    }

    public static NotificationMessage standard(
            String title, String message, NotificationType type, String linkUrl) {
        return new NotificationMessage(title, message, type, linkUrl, false);
    }

    public static NotificationMessage mandatory(
            String title, String message, NotificationType type, String linkUrl) {
        return new NotificationMessage(title, message, type, linkUrl, true);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
