package com.sep490.backend.dto.request;

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

    private String mode; // tutor, grammar, translation, conversation, culture_guide, travel, regional

    /** UI response language: en or vi. */
    private String language;
}
