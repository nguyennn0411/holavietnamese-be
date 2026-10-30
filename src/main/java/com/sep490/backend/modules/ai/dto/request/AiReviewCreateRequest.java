package com.sep490.backend.modules.ai.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewCreateRequest {
    private String sessionId;
    private String scenarioId;
    private String scenarioTitle;

    @NotNull(message = "Rating is required")
    @Min(1)
    @Max(5)
    private Integer rating;

    private String userFeedback;
    private List<String> taggedIssues;
}
