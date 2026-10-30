package com.sep490.backend.modules.culture.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsefulPhraseResponse {
    private String word;
    private String pronunciation;
    private String meaning;
    private String context;
    private String audioUrl;
}
