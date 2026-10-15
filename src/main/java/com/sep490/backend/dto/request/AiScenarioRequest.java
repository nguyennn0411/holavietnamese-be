package com.sep490.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiScenarioRequest {
    private String id;

    @NotBlank(message = "Title is required")
    private String title;

    private String level;
    private String topic;
    private String destination;
    private String difficulty;
    private String duration;
    private String thumbnail;
    private String coverImage;
    private String overview;

    // AI Role
    private String aiRoleName;
    private String aiRoleTitle;
    private String aiRoleAvatar;
    private String aiRoleTone;
    private String aiRoleInitialGreeting;

    // Learner Role
    private String learnerRoleTitle;
    private String learnerRoleContext;

    // JSON fields as objects
    private List<Object> objectives;
    private List<Object> vocabularyHints;
    private String culturalTip;
    private List<String> sampleHints;

    private Integer maxTurns;
    private String status;
}
