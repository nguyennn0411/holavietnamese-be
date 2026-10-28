package com.sep490.backend.modules.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiScenarioResponse {
    private String id;
    private String title;
    private String level;
    private String levelLabel;
    private String topic;
    private String topicLabel;
    private String destination;
    private String difficulty;
    private String duration;
    private String thumbnail;
    private String coverImage;
    private String overview;

    // AI Role
    private Map<String, Object> aiRole;

    // Learner Role
    private Map<String, Object> learnerRole;

    // Objectives, vocabulary hints, etc.
    private List<Object> objectives;
    private List<Object> vocabularyHints;
    private String culturalTip;
    private List<String> sampleHints;

    private Integer maxTurns;
    private Integer completionsCount;
    private Integer avgScore;
    private String status;
}
