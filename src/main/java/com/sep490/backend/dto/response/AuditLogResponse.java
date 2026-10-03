package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AuditLogResponse {
    private Long id;
    private String adminUsername;
    private String action;
    private String target;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}
