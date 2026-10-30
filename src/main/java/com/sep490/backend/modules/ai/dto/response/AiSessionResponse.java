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
public class AiSessionResponse {
    private String id;
    private Long userId;
    private String mode;
    private String title;
    private String scenarioId;
    private String status;
    private Integer turnsCount;
    private Integer score;
    private String feedbackSummary;
    private String contextSummary;
    private List<AiMessageResponse> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime endedAt;
}
