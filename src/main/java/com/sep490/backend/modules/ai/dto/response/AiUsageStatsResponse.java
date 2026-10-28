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
public class AiUsageStatsResponse {
    private Map<String, Object> summary;
    private List<Map<String, Object>> modeBreakdown;
    private List<Map<String, Object>> recentLogs;
}
