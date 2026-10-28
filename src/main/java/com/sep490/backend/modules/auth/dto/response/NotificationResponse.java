package com.sep490.backend.modules.auth.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private String type;
    private String linkUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
