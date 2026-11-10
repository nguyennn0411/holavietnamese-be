package com.sep490.backend.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageSendRequest {
    @NotBlank(message = "Message text cannot be blank (VAL-10)")
    @Size(max = 2000, message = "Message text must not exceed 2000 characters (VAL-10)")
    private String text;

    private String mode; // tutor, grammar, translation, conversation, culture_guide, travel, regional, course_coach

    /** UI response language: en or vi. */
    private String language;

    // AI Course Coach specific fields
    private Long courseId;
    private Long lessonId;
    private String lessonTitle;
    private String lessonContentSnippet;
    private String coachMode; // ask, summarize, explain, give_examples, practice, quiz_prep
}
