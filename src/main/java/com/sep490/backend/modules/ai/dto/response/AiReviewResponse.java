package com.sep490.backend.modules.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewResponse {
    private String id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String sessionId;
    private String scenarioId;
    private String scenarioTitle;
    private Integer rating;
    private String userFeedback;
    private List<String> taggedIssues;
    private String adminNote;
    private Boolean reviewedByAdmin;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
