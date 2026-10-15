package com.sep490.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionCreateRequest {
    @NotBlank(message = "Mode is required")
    private String mode; // tutor, grammar, translation, conversation, culture_guide, travel, regional

    private String title;
    private String scenarioId; // optional when creating a roleplay session
}
