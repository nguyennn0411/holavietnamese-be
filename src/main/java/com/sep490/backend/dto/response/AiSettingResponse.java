package com.sep490.backend.dto.response;

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
public class AiSettingResponse {
    private String activeModel;
    private List<Map<String, Object>> allowedModels;
    private String systemPromptVersion;
    private List<Map<String, Object>> promptVersionsHistory;
    private Map<String, Object> limits;
    private Map<String, Object> security;
    private List<String> enabledModes;
    private Boolean apiKeyConfigured;
    private String apiKeyMasked;
}
