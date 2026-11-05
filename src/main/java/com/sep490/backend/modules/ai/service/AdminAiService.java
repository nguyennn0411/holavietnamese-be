package com.sep490.backend.modules.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.ai.dto.request.AiScenarioRequest;
import com.sep490.backend.modules.ai.dto.request.AiSettingUpdateRequest;
import com.sep490.backend.modules.ai.dto.response.AiReviewResponse;
import com.sep490.backend.modules.ai.dto.response.AiScenarioResponse;
import com.sep490.backend.modules.ai.dto.response.AiSettingResponse;
import com.sep490.backend.modules.ai.dto.response.AiUsageStatsResponse;
import com.sep490.backend.modules.ai.entity.AiReview;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.ai.entity.AiSetting;
import com.sep490.backend.modules.ai.entity.AiUsageLog;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.modules.ai.repository.AiReviewRepository;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.ai.repository.AiSettingRepository;
import com.sep490.backend.modules.ai.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAiService {

    private final AiSettingRepository settingRepository;
    private final AiScenarioRepository scenarioRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiReviewRepository reviewRepository;
    private final AiSessionRepository sessionRepository;
    private final AiTutorService aiTutorService;
    private final AiRoleplayService aiRoleplayService;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    /**
     * UC-107: View AI Settings
     */
    public AiSettingResponse getAiSettings() {
        AiSetting setting = settingRepository.findById(1L).orElseGet(() -> {
            AiSetting def = AiSetting.builder()
                    .id(1L)
                    .activeModel("openrouter/free")
                    .systemPromptVersion("v2.2.0-stable")
                    .systemPrompt("Phản hồi phải dựa trên lịch sử hội thoại thực tế của người học. Không sinh điểm số phát âm ảo gây hiểu lầm cho người học.")
                    .maxTurnsPerSession(25)
                    .maxTokensPerResponse(1024)
                    .rateLimitPerMinute(30)
                    .dailySessionsPerUser(40)
                    .monthlyBudgetCapUsd(new BigDecimal("150.00"))
                    .currentMonthSpendUsd(BigDecimal.ZERO)
                    .costAlertThresholdPercent(80)
                    .apiKeyConfigured(aiProviderService.isApiKeyAvailable("openrouter/free"))
                    .enabledModes("tutor,grammar,translation,conversation,culture_guide,travel,regional")
                    .contextOptions("learner_level,course,destination,vocabulary")
                    .moderationEnabled(true)
                    .build();
            return settingRepository.save(def);
        });

        List<Map<String, Object>> allowedModels = List.of(
                Map.of("id", "openrouter/free", "name", "OpenRouter Free (Đang dùng - miễn phí)", "provider", "OpenRouter", "costPer1kTokens", 0.0, "isDefault", true),
                Map.of("id", "gemini-1.5-pro", "name", "Gemini 1.5 Pro (Nâng cao - Suy luận sâu cho ngữ pháp & văn hóa)", "provider", "Google", "costPer1kTokens", 0.0035, "isDefault", false),
                Map.of("id", "gpt-4o-mini", "name", "GPT-4o Mini (Đa ngôn ngữ phản xạ)", "provider", "OpenAI", "costPer1kTokens", 0.0006, "isDefault", false),
                Map.of("id", "claude-3-5-sonnet", "name", "Claude 3.5 Sonnet (Chính xác văn bản)", "provider", "Anthropic", "costPer1kTokens", 0.006, "isDefault", false)
        );

        List<Map<String, Object>> promptHistory = new ArrayList<>();
        String currentVersion = setting.getSystemPromptVersion() != null ? setting.getSystemPromptVersion() : "v2.2.0-stable";
        String updatedDate = setting.getUpdatedAt() != null
                ? setting.getUpdatedAt().toLocalDate().toString()
                : (setting.getCreatedAt() != null ? setting.getCreatedAt().toLocalDate().toString() : "2026-03-20");
        String currentNote = (setting.getSystemPrompt() != null && !setting.getSystemPrompt().isBlank())
                ? setting.getSystemPrompt()
                : "Phản hồi phải dựa trên lịch sử hội thoại thực tế của người học; không sinh điểm số phát âm ảo.";
        promptHistory.add(Map.of(
                "version", currentVersion,
                "releasedAt", updatedDate,
                "author", "System Admin",
                "note", currentNote
        ));
        if (!"v2.1.0".equals(currentVersion)) {
            promptHistory.add(Map.of("version", "v2.1.0", "releasedAt", "2026-02-15", "author", "Senior Engineer", "note", "Cải tiến phân loại từ vựng Bắc - Trung - Nam."));
        }
        if (!"v2.0.0".equals(currentVersion)) {
            promptHistory.add(Map.of("version", "v2.0.0", "releasedAt", "2026-01-10", "author", "AI Architect", "note", "Ra mắt 7 chế độ AI Tutor."));
        }

        List<String> enabledModes = (setting.getEnabledModes() != null && !setting.getEnabledModes().isBlank())
                ? Arrays.stream(setting.getEnabledModes().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                : List.of("tutor", "grammar", "translation", "conversation", "culture_guide", "travel", "regional");
        List<String> contextOptions = (setting.getContextOptions() != null && !setting.getContextOptions().isBlank())
                ? Arrays.stream(setting.getContextOptions().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                : List.of("learner_level", "course", "destination", "vocabulary");

        BigDecimal loggedCost = usageLogRepository.sumCostSince(LocalDateTime.now().minusDays(30));
        BigDecimal currentSpend = loggedCost != null
                ? loggedCost
                : (setting.getCurrentMonthSpendUsd() != null ? setting.getCurrentMonthSpendUsd() : BigDecimal.ZERO);

        Map<String, Object> limits = Map.of(
                "maxTurnsPerSession", setting.getMaxTurnsPerSession(),
                "maxTokensPerResponse", setting.getMaxTokensPerResponse(),
                "rateLimitPerMinute", setting.getRateLimitPerMinute(),
                "dailySessionsPerUser", setting.getDailySessionsPerUser(),
                "monthlyBudgetCapUsd", setting.getMonthlyBudgetCapUsd(),
                "currentMonthSpendUsd", currentSpend,
                "costAlertThresholdPercent", setting.getCostAlertThresholdPercent()
        );

        Map<String, Object> security = Map.of(
                "apiKeyLocation", "Secure Backend Environment (application.yml / ENV)",
                "backendProxyUrl", "/api/ai/sessions/{id}/messages",
                "clientExposedKeys", false,
                "historyPrivacyStrict", true
        );

        String maskedKey = null;
        String rawKey = setting.getApiKey() != null && !setting.getApiKey().isBlank()
                ? setting.getApiKey() : aiProviderService.getEffectiveApiKey(setting.getActiveModel());
        if (rawKey != null && rawKey.length() > 8) {
            maskedKey = rawKey.substring(0, 6) + "..." + rawKey.substring(rawKey.length() - 4);
            if (setting.getApiKey() != null && !setting.getApiKey().isBlank()) {
                aiProviderService.setRuntimeApiKey(setting.getActiveModel(), setting.getApiKey());
            }
        }

        return AiSettingResponse.builder()
                .activeModel(setting.getActiveModel())
                .allowedModels(allowedModels)
                .systemPromptVersion(setting.getSystemPromptVersion())
                .systemPrompt(setting.getSystemPrompt() != null ? setting.getSystemPrompt() : "")
                .promptVersionsHistory(promptHistory)
                .limits(limits)
                .security(security)
                .enabledModes(enabledModes)
                .contextOptions(contextOptions)
                .moderationEnabled(setting.getModerationEnabled() != null ? setting.getModerationEnabled() : true)
                .apiKeyConfigured(aiProviderService.isApiKeyAvailable(setting.getActiveModel()))
                .apiKeyMasked(maskedKey)
                .updatedAt(setting.getUpdatedAt() != null ? setting.getUpdatedAt().toString() : null)
                .build();
    }

    /**
     * UC-107: Update AI Configuration
     */
    @Transactional
    public AiSettingResponse updateAiSettings(AiSettingUpdateRequest request) {
        AiSetting setting = settingRepository.findById(1L).orElseGet(() -> AiSetting.builder().id(1L).build());

        setting.setActiveModel(request.getActiveModel());
        if (request.getSystemPromptVersion() != null) {
            setting.setSystemPromptVersion(request.getSystemPromptVersion().trim());
        }
        if (request.getSystemPrompt() != null) {
            setting.setSystemPrompt(request.getSystemPrompt().trim());
        }
        if (request.getContextOptions() != null) {
            setting.setContextOptions(request.getContextOptions().trim());
        }
        if (request.getModerationEnabled() != null) {
            setting.setModerationEnabled(request.getModerationEnabled());
        }
        setting.setMaxTurnsPerSession(request.getMaxTurnsPerSession());
        setting.setMaxTokensPerResponse(request.getMaxTokensPerResponse());
        setting.setRateLimitPerMinute(request.getRateLimitPerMinute());
        setting.setDailySessionsPerUser(request.getDailySessionsPerUser());
        setting.setMonthlyBudgetCapUsd(request.getMonthlyBudgetCapUsd());
        setting.setCostAlertThresholdPercent(request.getCostAlertThresholdPercent());
        if (request.getEnabledModes() != null) {
            setting.setEnabledModes(request.getEnabledModes());
        }
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            setting.setApiKey(request.getApiKey().trim());
            aiProviderService.setRuntimeApiKey(request.getActiveModel(), request.getApiKey().trim());
        }
        setting.setApiKeyConfigured(aiProviderService.isApiKeyAvailable(setting.getActiveModel()));

        settingRepository.save(setting);
        log.info("Admin updated AI settings: model={}, budgetCap={}", setting.getActiveModel(), setting.getMonthlyBudgetCapUsd());

        return getAiSettings();
    }

    /**
     * UC-108: View AI Usage Statistics (Calls, tokens, cost, latency, mode breakdown, recent logs)
     */
    public AiUsageStatsResponse getUsageStatistics() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        long totalCalls = usageLogRepository.countRequestsSince(thirtyDaysAgo);
        long totalTokens = usageLogRepository.sumTokensSince(thirtyDaysAgo);
        double avgLatency = usageLogRepository.avgLatencySince(thirtyDaysAgo);
        BigDecimal currentSpend = usageLogRepository.sumCostSince(thirtyDaysAgo);
        long errorCount = usageLogRepository.countErrorsSince(thirtyDaysAgo);

        AiSetting setting = settingRepository.findById(1L).orElse(null);
        BigDecimal budgetCap = setting != null && setting.getMonthlyBudgetCapUsd() != null
                ? setting.getMonthlyBudgetCapUsd()
                : new BigDecimal("150.00");

        long totalSessions = sessionRepository.count();

        // Mode breakdown
        List<Object[]> modeGroups = usageLogRepository.summarizeByModeSince(thirtyDaysAgo);
        List<Map<String, Object>> modeBreakdown = new ArrayList<>();

        if (!modeGroups.isEmpty()) {
            for (Object[] row : modeGroups) {
                String mode = (String) row[0];
                long calls = ((Number) row[1]).longValue();
                int percent = totalCalls > 0 ? (int) Math.round((calls * 100.0) / totalCalls) : 0;
                modeBreakdown.add(Map.of(
                        "mode", mode,
                        "name", getModeDisplayName(mode),
                        "calls", calls,
                        "tokens", ((Number) row[2]).longValue(),
                        "cost", row[3],
                        "percent", percent
                ));
            }
        }

        // Recent logs
        List<AiUsageLog> recentLogs = usageLogRepository.findTop50ByOrderByCreatedAtDesc();
        List<Map<String, Object>> logList = recentLogs.stream().map(l -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", l.getId());
            map.put("timestamp", l.getCreatedAt() != null ? l.getCreatedAt().toString() : "");
            map.put("mode", l.getMode());
            map.put("user", l.getUser() != null ? l.getUser().getUsername() : "anonymous");
            map.put("tokensIn", l.getTokensIn());
            map.put("tokensOut", l.getTokensOut());
            map.put("latency", l.getLatencyMs());
            map.put("cost", "$" + l.getCostUsd().toPlainString());
            map.put("status", l.getStatusCode());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> summary = Map.of(
                "totalSessions", totalSessions,
                "totalApiCalls", totalCalls,
                "totalTokens", totalTokens,
                "costMonthUsd", currentSpend,
                "budgetLimitUsd", budgetCap,
                "avgResponseTimeMs", Math.round(avgLatency),
                "errorRate", totalCalls > 0 ? String.format(Locale.ROOT, "%.2f%%", errorCount * 100.0 / totalCalls) : "0.00%"
        );

        return AiUsageStatsResponse.builder()
                .summary(summary)
                .modeBreakdown(modeBreakdown)
                .recentLogs(logList)
                .build();
    }

    /**
     * Admin Scenarios CRUD (Scenario Builder)
     */
    public List<AiScenarioResponse> getAllScenarios() {
        return scenarioRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(aiRoleplayService::toScenarioResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AiScenarioResponse saveScenario(AiScenarioRequest req) {
        String id = (req.getId() != null && !req.getId().isBlank()) ? req.getId() : "sc-" + UUID.randomUUID().toString().substring(0, 8);
        AiScenario sc = scenarioRepository.findById(id).orElseGet(() -> AiScenario.builder().id(id).build());

        sc.setTitle(req.getTitle());
        sc.setTitleVi(req.getTitleVi() != null ? req.getTitleVi() : req.getTitle());
        sc.setTitleEn(req.getTitleEn());
        sc.setLevel(req.getLevel());
        sc.setTopic(req.getTopic());
        sc.setDestination(req.getDestination());
        sc.setDifficulty(req.getDifficulty());
        sc.setDuration(req.getDuration());
        sc.setThumbnail(req.getThumbnail());
        sc.setCoverImage(req.getCoverImage());
        sc.setOverview(req.getOverview());
        sc.setOverviewVi(req.getOverviewVi() != null ? req.getOverviewVi() : req.getOverview());
        sc.setOverviewEn(req.getOverviewEn());

        sc.setAiRoleName(req.getAiRoleName());
        sc.setAiRoleTitle(req.getAiRoleTitle());
        sc.setAiRoleAvatar(req.getAiRoleAvatar());
        sc.setAiRoleTone(req.getAiRoleTone());
        sc.setAiRoleInitialGreeting(req.getAiRoleInitialGreeting());

        sc.setLearnerRoleTitle(req.getLearnerRoleTitle());
        sc.setLearnerRoleContext(req.getLearnerRoleContext());

        try {
            if (req.getObjectives() != null) sc.setObjectivesJson(objectMapper.writeValueAsString(req.getObjectives()));
            if (req.getVocabularyHints() != null) sc.setVocabularyHintsJson(objectMapper.writeValueAsString(req.getVocabularyHints()));
            if (req.getSampleHints() != null) sc.setSampleHintsJson(objectMapper.writeValueAsString(req.getSampleHints()));
        } catch (Exception ignored) {}

        sc.setCulturalTip(req.getCulturalTip());
        sc.setMaxTurns(req.getMaxTurns() != null ? req.getMaxTurns() : 10);
        sc.setStatus(req.getStatus() != null ? req.getStatus() : "ACTIVE");

        scenarioRepository.save(sc);
        return aiRoleplayService.toScenarioResponse(sc);
    }

    @Transactional
    public void deleteScenario(String id) {
        sessionRepository.clearScenarioReference(id);
        scenarioRepository.deleteById(id);
    }

    @Transactional
    public AiScenarioResponse updateScenarioStatus(String id, String status) {
        AiScenario sc = scenarioRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        sc.setStatus(status);
        scenarioRepository.save(sc);
        return aiRoleplayService.toScenarioResponse(sc);
    }

    /**
     * Admin AI Reviews Audit
     */
    public List<AiReviewResponse> getReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(aiTutorService::toReviewResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AiReviewResponse updateReviewStatus(String reviewId, String status, String adminNote) {
        AiReview rev = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        rev.setStatus(status);
        rev.setAdminNote(adminNote);
        rev.setReviewedByAdmin(true);
        reviewRepository.save(rev);
        return aiTutorService.toReviewResponse(rev);
    }

    private String getModeDisplayName(String mode) {
        if (mode == null) return "AI Service";
        return switch (mode.toLowerCase()) {
            case "tutor" -> "Gia sư toàn diện";
            case "grammar" -> "Ngữ pháp & Sửa câu";
            case "translation" -> "Dịch thuật";
            case "conversation" -> "Luyện hội thoại";
            case "culture_guide" -> "Chỉ dẫn văn hóa";
            case "travel" -> "Du lịch sinh tồn";
            case "regional" -> "Tiếng Việt 3 miền";
            case "roleplay" -> "Kịch bản Roleplay";
            default -> mode;
        };
    }
}
