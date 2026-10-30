package com.sep490.backend.common.notification;

/**
 * Business-facing notification boundary. Feature modules do not need to know whether a message
 * is stored, streamed over SSE, or delivered through another channel later.
 */
public interface NotificationPort {

    boolean send(Long userId, NotificationMessage message);

    boolean sendOnce(Long userId, NotificationMessage message, String eventKey);

    int sendToRole(String role, NotificationMessage message);

    int broadcast(NotificationMessage message);
}
