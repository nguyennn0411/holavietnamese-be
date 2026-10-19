package com.sep490.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageResponse {
    private String id;
    private String sender; // 'user' or 'ai'
    private String name;
    private String avatar;
    private String text;
    private String translation;
    private String explanation;
    private String nativeAlternative;
    private String culturalNote;
    private String grammarCorrection;
    private String feedbackJson;
    private java.util.List<java.util.Map<String, String>> savedWords;
    private LocalDateTime timestamp;
}
