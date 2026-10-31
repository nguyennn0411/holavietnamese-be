package com.sep490.backend.modules.ai.service;
import com.sep490.backend.modules.ai.client.GeminiApiClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.ai.dto.request.AiMessageSendRequest;
import com.sep490.backend.modules.ai.dto.request.AiReviewCreateRequest;
import com.sep490.backend.modules.ai.dto.request.AiSessionCreateRequest;
import com.sep490.backend.modules.ai.dto.request.AiVocabularyEnrichmentRequest;
import com.sep490.backend.modules.ai.dto.response.AiMessageResponse;
import com.sep490.backend.modules.ai.dto.response.AiReviewResponse;
import com.sep490.backend.modules.ai.dto.response.AiSessionResponse;
import com.sep490.backend.modules.ai.dto.response.AiVocabularyEnrichmentResponse;
import com.sep490.backend.modules.ai.entity.AiMessage;
import com.sep490.backend.modules.ai.entity.AiReview;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.ai.entity.AiSession;
import com.sep490.backend.modules.ai.entity.AiSetting;
import com.sep490.backend.modules.ai.entity.AiUsageLog;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.modules.ai.repository.AiMessageRepository;
import com.sep490.backend.modules.ai.repository.AiReviewRepository;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.ai.repository.AiSettingRepository;
import com.sep490.backend.modules.ai.repository.AiUsageLogRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTutorService {

    private final AiSessionRepository sessionRepository;
    private final AiMessageRepository messageRepository;
    private final AiSettingRepository settingRepository;
    private final AiScenarioRepository scenarioRepository;
    private final AiReviewRepository reviewRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final UserRepository userRepository;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper;
    private final IntentClassifier intentClassifier;

    public AiVocabularyEnrichmentResponse enrichVocabulary(AiVocabularyEnrichmentRequest request) {
        User user = getCurrentUser();
        AiSetting settings = getOrInitSettings();
        String model = settings.getActiveModel() != null ? settings.getActiveModel() : "openrouter/free";
        if (!aiProviderService.isApiKeyAvailable(model)) {
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }

        String selectedWord = request.word().trim();
        String context = request.context() == null ? "" : request.context().trim();
        String lessonTitle = request.lessonTitle() == null ? "" : request.lessonTitle().trim();
        String prompt = "Selected Vietnamese text: " + selectedWord
                + "\nLesson: " + lessonTitle
                + "\nNearby context: " + context
                + "\nReturn only a valid JSON object with exactly these string fields: "
                + "word, meaning, pronunciation, exampleSentence, note. "
                + "Keep word exactly as selected. meaning must be a concise English translation. "
                + "pronunciation should help an English-speaking learner. "
                + "exampleSentence must be a natural Vietnamese sentence using the selected text. "
                + "note must briefly explain usage or nuance in English. Do not use markdown.";

        try {
            var result = aiProviderService.generateContent(
                    "You are a precise Vietnamese-language dictionary assistant. Use lesson context to choose the correct meaning.",
                    prompt,
                    model
            );
            if (result == null || result.text() == null || result.text().isBlank()) {
                throw new AppException(ErrorCode.AI_UNAVAILABLE);
            }

            String raw = result.text().trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new AppException(ErrorCode.AI_UNAVAILABLE);
            }
            AiVocabularyEnrichmentResponse parsed = objectMapper.readValue(
                    raw.substring(start, end + 1),
                    AiVocabularyEnrichmentResponse.class
            );
            if (parsed.meaning() == null || parsed.meaning().isBlank()) {
                throw new AppException(ErrorCode.AI_UNAVAILABLE);
            }

            logUsage(user, "vocabulary-enrichment", "vocabulary", result.tokensIn(), result.tokensOut(),
                    (int) result.latencyMs(), 200, null);
            return new AiVocabularyEnrichmentResponse(
                    selectedWord,
                    parsed.meaning().trim(),
                    trimToLength(parsed.pronunciation(), 200),
                    trimToLength(parsed.exampleSentence(), 2000),
                    trimToLength(parsed.note(), 2000)
            );
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Could not enrich selected vocabulary '{}': {}", selectedWord, exception.getMessage());
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private static String trimToLength(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /**
     * UC-099: Open AI Tutor & UC-100: Start AI Conversation
     * Creates a new session with initial AI greeting in the requested mode.
     */
    @Transactional
    public AiSessionResponse createSession(AiSessionCreateRequest request) {
        User currentUser = getCurrentUser();
        AiSetting settings = getOrInitSettings();

        // Check daily session quota (BR-24)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long userTodaySessions = sessionRepository.countByUserIdAndCreatedAtAfter(currentUser.getId(), startOfDay);
        if (settings.getDailySessionsPerUser() != null && userTodaySessions >= settings.getDailySessionsPerUser()) {
            throw new AppException(ErrorCode.AI_QUOTA_EXCEEDED);
        }

        String mode = (request.getMode() != null && !request.getMode().isBlank()) ? request.getMode() : "tutor";
        String sessionId = resolveUserSessionId(request.getId(), currentUser.getId());
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle()
                : "Hội thoại AI (" + getModeTitle(mode) + ")";

        Optional<AiSession> existingOpt = sessionRepository.findByIdAndUserId(sessionId, currentUser.getId());
        if (existingOpt.isPresent()) {
            AiSession existing = existingOpt.get();
            List<AiMessage> existingMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(existing.getId());
            return toSessionResponse(existing, existingMessages);
        }

        AiScenario scenario = (request.getScenarioId() != null && !request.getScenarioId().isBlank())
                ? scenarioRepository.findById(request.getScenarioId()).orElse(null)
                : null;

        AiSession session = AiSession.builder()
                .id(sessionId)
                .user(currentUser)
                .mode(mode)
                .title(title)
                .scenario(scenario)
                .status("ACTIVE")
                .turnsCount(0)
                .build();
        sessionRepository.save(session);

        // Generate welcoming opening message for the chosen mode
        String welcomeText = generateInitialGreetingText(session, currentUser, settings);
        AiMessage initialMessage = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("ai")
                .name("Hola AI Tutor")
                .avatar(getModeIcon(mode))
                .mode(mode)
                .text(welcomeText)
                .explanation("Tôi đã sẵn sàng đồng hành cùng bạn trong chủ đề " + getModeTitle(mode) + ".")
                .build();
        messageRepository.save(initialMessage);

        return toSessionResponse(session, List.of(initialMessage));
    }

    /**
     * UC-103: View AI Session History (Learner sessions filtered by mode or all)
     */
    @Transactional(readOnly = true)
    public List<AiSessionResponse> getLearnerSessions(String mode) {
        User currentUser = getCurrentUser();
        List<AiSession> sessions;
        if (mode != null && !mode.isBlank() && !"all".equalsIgnoreCase(mode)) {
            sessions = sessionRepository.findByUserIdAndModeOrderByCreatedAtDesc(currentUser.getId(), mode);
        } else {
            sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                    .filter(s -> !"roleplay".equalsIgnoreCase(s.getMode()))
                    .collect(Collectors.toList());
        }

        return sessions.stream()
                .map(s -> toSessionResponse(s, messageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Get details and messages of a session
     */
    @Transactional
    public AiSessionResponse getSessionDetails(String sessionId) {
        User currentUser = getCurrentUser();
        String resolvedSessionId = resolveUserSessionId(sessionId, currentUser.getId());
        AiSession session = sessionRepository.findByIdAndUserId(resolvedSessionId, currentUser.getId())
                .orElseGet(() -> {
                    AiSession newSession = AiSession.builder()
                            .id(resolvedSessionId)
                            .user(currentUser)
                            .mode("tutor")
                            .title("Hội thoại AI Tutor")
                            .status("ACTIVE")
                            .turnsCount(0)
                            .build();
                    return sessionRepository.save(newSession);
                });

        List<AiMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        return toSessionResponse(session, messages);
    }

    @Transactional
    public AiSessionResponse updateSession(String sessionId, Map<String, String> updates) {
        User currentUser = getCurrentUser();
        String resolvedSessionId = resolveUserSessionId(sessionId, currentUser.getId());
        AiSession session = sessionRepository.findByIdAndUserId(resolvedSessionId, currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        if (updates != null) {
            String newTitle = updates.get("title");
            if (newTitle != null && !newTitle.isBlank()) {
                session.setTitle(newTitle.trim());
            }
            String newMode = updates.get("mode");
            if (newMode != null && !newMode.isBlank()) {
                session.setMode(newMode.trim());
            }
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        List<AiMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        return toSessionResponse(session, messages);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        User currentUser = getCurrentUser();
        String resolvedSessionId = resolveUserSessionId(sessionId, currentUser.getId());
        sessionRepository.findByIdAndUserId(resolvedSessionId, currentUser.getId())
                .ifPresent(sessionRepository::delete);
    }

    /**
     * UC-101: Send AI Message & UC-106: Receive Language Feedback
     * BR-22: Builds contextual prompt using learner's level, destination, native language.
     * VAL-10: Reject message > 2000 characters or blank.
     */
    @Transactional
    public AiMessageResponse sendMessage(String sessionId, AiMessageSendRequest request) {
        User currentUser = getCurrentUser();
        String resolvedSessionId = resolveUserSessionId(sessionId, currentUser.getId());
        AiSession session = sessionRepository.findByIdAndUserId(resolvedSessionId, currentUser.getId())
                .orElseGet(() -> {
                    String mode = (request.getMode() != null && !request.getMode().isBlank()) ? request.getMode() : "tutor";
                    String title = "Hội thoại AI (" + getModeTitle(mode) + ")";
                    AiSession newSession = AiSession.builder()
                            .id(resolvedSessionId)
                            .user(currentUser)
                            .mode(mode)
                            .title(title)
                            .status("ACTIVE")
                            .turnsCount(0)
                            .build();
                    return sessionRepository.save(newSession);
                });

        if (!"ACTIVE".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalStateException("Hội thoại này đã kết thúc.");
        }

        String userText = request.getText().replaceAll("(?s)\\n*\\[(?:Language rule|Quy tắc ngôn ngữ):.*\\]\\s*$", "").trim();
        if (userText.isEmpty() || userText.length() > 2000) {
            throw new IllegalArgumentException("Nội dung tin nhắn không hợp lệ (VAL-10).");
        }

        AiSetting settings = getOrInitSettings();
        List<AiMessage> previousMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        // 1. Save user message
        AiMessage userMessage = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("user")
                .name(currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getUsername())
                .mode(session.getMode())
                .text(userText)
                .build();
        messageRepository.save(userMessage);

        // 2. Classify intent and build learner profile & working memory
        ConversationIntent intent = intentClassifier.classify(userText, previousMessages);
        String learnerLevel = currentUser.getTargetLevel() != null ? currentUser.getTargetLevel() : "A1";
        String nativeLang = resolveResponseLanguage(userText, request.getLanguage(), currentUser.getNativeLanguage());
        String learningGoal = currentUser.getLearningGoal() != null ? currentUser.getLearningGoal() : "Everyday conversations";
        String mode = (request.getMode() != null && !request.getMode().isBlank()) ? request.getMode() : session.getMode();

        // Update working memory / context summary if needed
        updateContextSummaryIfNeeded(session, previousMessages, userText);

        String systemPrompt = buildSystemPrompt(mode, learnerLevel, nativeLang, learningGoal, intent, session.getContextSummary());
        List<Map<String, Object>> multiTurnContents = buildMultiTurnContents(previousMessages, userText, nativeLang);

        // 3. Invoke Gemini with official systemInstruction & native multi-turn conversation memory
        GeminiApiClient.GeminiResponse geminiRes = aiProviderService.generateMultiTurn(
                systemPrompt, multiTurnContents, settings.getActiveModel());

        AiMessage aiMessage;
        int tokensIn = 120;
        int tokensOut = 180;
        int latencyMs = 280;

        if (geminiRes != null && geminiRes.text() != null) {
            tokensIn = geminiRes.tokensIn();
            tokensOut = geminiRes.tokensOut();
            latencyMs = (int) geminiRes.latencyMs();
            aiMessage = parseAndBuildAiMessage(session, geminiRes.text(), mode, nativeLang);
        } else {
            // High quality fallback responder matching intent, context, and previous messages
            aiMessage = generateOfflineTutorResponse(session, userText, mode, learnerLevel, intent, previousMessages, nativeLang);
        }

        messageRepository.save(aiMessage);

        // 4. Update session turns
        session.setTurnsCount((session.getTurnsCount() == null ? 0 : session.getTurnsCount()) + 1);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // 5. Log AI usage (UC-108 & JOB-05)
        logUsage(currentUser, session.getId(), mode, tokensIn, tokensOut, latencyMs, 200, null);

        return toMessageResponse(aiMessage);
    }

    /**
     * UC-102: End AI Session
     */
    @Transactional
    public AiSessionResponse endSession(String sessionId) {
        User currentUser = getCurrentUser();
        String resolvedSessionId = resolveUserSessionId(sessionId, currentUser.getId());
        AiSession session = sessionRepository.findByIdAndUserId(resolvedSessionId, currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());
        if (session.getFeedbackSummary() == null) {
            session.setFeedbackSummary("Hội thoại hoàn tất với " + (session.getTurnsCount() != null ? session.getTurnsCount() : 0) + " lượt trao đổi.");
        }
        sessionRepository.save(session);

        return toSessionResponse(session, null);
    }

    /**
     * Learner submits quality review for a session (UC-106 / Screen 9.1.5)
     */
    @Transactional
    public AiReviewResponse submitReview(AiReviewCreateRequest request) {
        User currentUser = getCurrentUser();
        String taggedJson = null;
        try {
            if (request.getTaggedIssues() != null) {
                taggedJson = objectMapper.writeValueAsString(request.getTaggedIssues());
            }
        } catch (Exception ignored) {}

        AiSession session = null;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            String resolvedId = resolveUserSessionId(request.getSessionId(), currentUser.getId());
            session = sessionRepository.findByIdAndUserId(resolvedId, currentUser.getId()).orElse(null);
        }

        if (session == null && currentUser != null) {
            List<AiSession> userSessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
            session = userSessions.stream()
                    .filter(s -> request.getScenarioId() != null && request.getScenarioId().equals(s.getScenarioId()))
                    .findFirst()
                    .orElse(null);

            if (session == null) {
                AiScenario scenario = (request.getScenarioId() != null && !request.getScenarioId().isBlank())
                        ? scenarioRepository.findById(request.getScenarioId()).orElse(null)
                        : null;
                String effectiveSessionId = resolveUserSessionId(request.getSessionId(), currentUser.getId());
                session = AiSession.builder()
                        .id(effectiveSessionId)
                        .user(currentUser)
                        .mode("roleplay")
                        .title(request.getScenarioTitle() != null ? request.getScenarioTitle() : "Phiên hội thoại AI")
                        .scenario(scenario)
                        .status("COMPLETED")
                        .turnsCount(4)

                        .totalScore(85)
                        .endedAt(LocalDateTime.now())
                        .build();
                sessionRepository.save(session);

                if (scenario != null && (scenario.getCompletionsCount() == null || scenario.getCompletionsCount() == 0)) {
                    scenario.setCompletionsCount(1);
                    scenario.setAvgScore(85);
                    scenarioRepository.save(scenario);
                }
            }
        }

        AiReview review = AiReview.builder()
                .id("review-" + UUID.randomUUID().toString().substring(0, 13))
                .user(currentUser)
                .session(session)
                .scenarioId(request.getScenarioId())
                .scenarioTitle(request.getScenarioTitle())
                .rating(request.getRating())
                .userFeedback(request.getUserFeedback())
                .taggedIssuesJson(taggedJson)
                .status("APPROVED")
                .reviewedByAdmin(false)
                .build();
        reviewRepository.save(review);

        return toReviewResponse(review);
    }

    // --- Helper Methods ---

    private String resolveUserSessionId(String rawSessionId, Long userId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return "session-" + UUID.randomUUID().toString().substring(0, 13);
        }
        String trimmed = rawSessionId.trim();
        String userSuffix = "-u" + userId;
        if ("session-welcome".equalsIgnoreCase(trimmed)) {
            return "session-welcome" + userSuffix;
        }
        Optional<AiSession> existing = sessionRepository.findById(trimmed);
        if (existing.isPresent()) {
            User owner = existing.get().getUser();
            if (owner != null && !owner.getId().equals(userId)) {
                return trimmed.endsWith(userSuffix) ? trimmed : (trimmed + userSuffix);
            }
        }
        return trimmed;
    }

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (auth.getPrincipal() instanceof com.sep490.backend.common.security.LearnerPrincipal lp && lp.id() != null) {
            return userRepository.findById(lp.id())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        }
        return userRepository.findActiveByUsernameWithRoles(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private AiSetting getOrInitSettings() {
        AiSetting settings = settingRepository.findById(1L).orElseGet(() -> {
            AiSetting def = AiSetting.builder()
                    .id(1L)
                    .activeModel("openrouter/free")
                    .systemPromptVersion("v2.2.0-stable")
                    .maxTurnsPerSession(25)
                    .maxTokensPerResponse(1024)
                    .rateLimitPerMinute(30)
                    .dailySessionsPerUser(40)
                    .monthlyBudgetCapUsd(new BigDecimal("150.00"))
                    .currentMonthSpendUsd(BigDecimal.ZERO)
                    .costAlertThresholdPercent(80)
                    .apiKeyConfigured(aiProviderService.isApiKeyAvailable("openrouter/free"))
                    .enabledModes("tutor,grammar,translation,conversation,culture_guide,travel,regional")
                    .build();
            return settingRepository.save(def);
        });
        if (settings.getApiKey() != null && !settings.getApiKey().isBlank()) {
            aiProviderService.setRuntimeApiKey(settings.getActiveModel(), settings.getApiKey());
        }
        return settings;
    }

    private void logUsage(User user, String sessionId, String mode, int in, int out, int latency, int status, String err) {
        BigDecimal cost = BigDecimal.valueOf((in * 0.00000035) + (out * 0.00000105));
        AiUsageLog usage = AiUsageLog.builder()
                .id("log-" + UUID.randomUUID().toString().substring(0, 8))
                .user(user)
                .sessionId(sessionId)
                .mode(mode)
                .tokensIn(in)
                .tokensOut(out)
                .latencyMs(latency)
                .costUsd(cost)
                .statusCode(status)
                .errorMessage(err)
                .build();
        usageLogRepository.save(usage);
    }

    public Map<String, Object> getAiStatus() {
        AiSetting settings = getOrInitSettings();
        String activeModel = settings.getActiveModel() != null ? settings.getActiveModel() : "openrouter/free";
        boolean isConfigured = aiProviderService.isApiKeyAvailable(activeModel);
        String rawKey = settings.getApiKey() != null && !settings.getApiKey().isBlank()
                ? settings.getApiKey()
                : aiProviderService.getEffectiveApiKey(activeModel);
        String masked = (rawKey != null && rawKey.length() > 8)
                ? rawKey.substring(0, 6) + "..." + rawKey.substring(rawKey.length() - 4)
                : null;

        User user = null;
        try {
            user = getCurrentUser();
        } catch (Exception ignored) {}

        long todaySessions = 0;
        if (user != null) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            todaySessions = sessionRepository.countByUserIdAndCreatedAtAfter(user.getId(), startOfDay);
        }

        int dailyCap = settings.getDailySessionsPerUser() != null ? settings.getDailySessionsPerUser() : 40;
        int remaining = Math.max(0, dailyCap - (int) todaySessions);

        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("isLiveCall", isConfigured);
        statusMap.put("apiKeyConfigured", isConfigured);
        statusMap.put("activeModel", settings.getActiveModel() != null ? settings.getActiveModel() : "openrouter/free");
        statusMap.put("apiKeyMasked", masked != null ? masked : "");
        String provider = activeModel != null && activeModel.toLowerCase(Locale.ROOT).startsWith("openrouter/")
                ? "OpenRouter"
                : (aiProviderService.isOpenAiModel(activeModel) ? "OpenAI" : "Google Gemini");
        statusMap.put("provider", provider);
        statusMap.put("conversationMode", isConfigured ? "generative" : "offline_fallback");
        statusMap.put("fallbackActive", !isConfigured);
        statusMap.put("dailySessionsRemaining", remaining);
        statusMap.put("dailySessionsCap", dailyCap);
        statusMap.put("maxTurnsPerSession", settings.getMaxTurnsPerSession() != null ? settings.getMaxTurnsPerSession() : 25);
        return statusMap;
    }

    @Transactional
    public Map<String, Object> configureApiKey(String apiKey) {
        return configureApiKey(apiKey, null);
    }

    @Transactional
    public Map<String, Object> configureApiKey(String apiKey, String requestedModel) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key không được để trống.");
        }
        AiSetting setting = getOrInitSettings();
        String cleanKey = apiKey.trim();
        String inferredModel = inferModelFromApiKey(cleanKey);
        if (requestedModel != null && !requestedModel.isBlank()) {
            if (!aiProviderService.isOpenAiModel(requestedModel)
                    && !requestedModel.toLowerCase().startsWith("gemini-")) {
                throw new IllegalArgumentException("Model AI không được hỗ trợ.");
            }
            setting.setActiveModel(requestedModel.trim());
        } else if (inferredModel != null) {
            // The learner-facing popup sends only the key. Select the matching
            // provider instead of storing an OpenRouter/OpenAI key for Gemini.
            setting.setActiveModel(inferredModel);
        }
        String activeModel = setting.getActiveModel();
        boolean isValid = aiProviderService.testApiKey(cleanKey, activeModel);
        setting.setApiKey(cleanKey);
        setting.setApiKeyConfigured(true);
        settingRepository.save(setting);

        aiProviderService.setRuntimeApiKey(activeModel, cleanKey);

        String masked = cleanKey.length() > 8
                ? cleanKey.substring(0, 6) + "..." + cleanKey.substring(cleanKey.length() - 4)
                : cleanKey;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("isValidLive", isValid);
        res.put("activeModel", activeModel);
        res.put("provider", aiProviderService.isOpenAiModel(activeModel) ? "OpenAI" : "Google Gemini");
        res.put("apiKeyMasked", masked);
        res.put("message", isValid
                ? "Đã kết nối thành công với " + (aiProviderService.isOpenAiModel(activeModel) ? "OpenAI" : "Google Gemini") + " API!"
                : "Đã lưu API Key. Lưu ý: Kiểm tra model, quota và quyền truy cập của nhà cung cấp.");
        return res;
    }

    private String inferModelFromApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return null;
        String normalized = apiKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sk-or-")) return "openrouter/free";
        if (normalized.startsWith("sk-")) return "gpt-4o-mini";
        return null;
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource res = new ClassPathResource("prompts/vietnamese-companion.txt");
            try (InputStream is = res.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load prompts/vietnamese-companion.txt from classpath, using builtin template: {}", e.getMessage());
            return """
                    # ROLE AND PERSONALITY
                    You are Hola — a friendly Vietnamese conversation partner for foreigners learning Vietnamese.
                    Your personality is warm, patient, curious, playful when appropriate, and respectful.
                    Help the learner communicate naturally in real-life Vietnamese situations through meaningful conversations, not constant grammar lessons.

                    # CURRENT CONTEXT
                    Learner level: {{level}}
                    Preferred explanation language: {{preferredLanguage}}
                    Current topic: {{currentTopic}}
                    Current learning goal: {{learningGoal}}
                    Detected intent: {{detectedIntent}}

                    # OUTPUT FORMAT
                    Return ONLY valid JSON:
                    {"text": "Conversational reply in the requested response language (1-4 natural sentences for casual chat)", "translation": null, "explanation": null, "nativeAlternative": null, "culturalNote": null, "grammarCorrection": null, "savedWords": []}
                    """;
        }
    }

    private String buildSystemPrompt(String mode, String level, String nativeLang, String learningGoal, ConversationIntent intent, String contextSummary) {
        String template = loadPromptTemplate();
        String currentTopic = getModeTitle(mode);
        String preferredLanguage = (nativeLang != null && !nativeLang.isBlank()) ? nativeLang : "English";
        String learnerLevel = (level != null && !level.isBlank()) ? level : "A1";
        String goal = (learningGoal != null && !learningGoal.isBlank()) ? learningGoal : "Everyday conversations";
        String detectedIntent = (intent != null) ? intent.name() : "CASUAL_CHAT";

        String contextMemory = (contextSummary != null && !contextSummary.isBlank())
                ? "Những thông tin người học đã chia sẻ:\n" + contextSummary + "\n(LƯU Ý QUAN TRỌNG: Ghi nhớ các thông tin trên để kết nối hội thoại tự nhiên, tuyệt đối KHÔNG hỏi lại những điều người học đã nói)."
                : "Chưa có thông tin ghi nhớ trước đó. Hãy lắng nghe và ghi nhớ chi tiết khi người học chia sẻ.";

        return template
                .replace("{{level}}", learnerLevel)
                .replace("{{preferredLanguage}}", preferredLanguage)
                .replace("{{currentTopic}}", currentTopic)
                .replace("{{learningGoal}}", goal)
                .replace("{{detectedIntent}}", detectedIntent)
                .replace("{{contextMemory}}", contextMemory)
                + ("English".equalsIgnoreCase(preferredLanguage)
                ? "\n\n# MANDATORY LANGUAGE INSTRUCTION\nThe learner asked in ENGLISH. You MUST write your response (`text` and `explanation`), all section headers (use '**Example:**' or '**Examples:**', NEVER '**Ví dụ:**'), parenthetical definitions, and the closing follow-up question (e.g., 'Have you used this phrase in any sentence? Or would you like me to help with pronunciation?') in ENGLISH."
                : "\n\n# QUY TẮC NGÔN NGỮ BẮT BUỘC\nNgười học đang hỏi bằng TIẾNG VIỆT. Bạn BẮT BUỘC phải trả lời (`text` và `explanation`), các tiêu đề mục (dùng '**Ví dụ:**', KHÔNG dùng '**Example:**') và câu hỏi gợi mở cuối câu (ví dụ: 'Bạn có dùng cụm từ này trong câu nào không? Hay muốn mình giúp phát âm?') hoàn toàn bằng TIẾNG VIỆT.");
    }

    public boolean isEnglishMessage(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.trim();
        boolean hasViDiacritics = trimmed.matches(".*[àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđÀÁẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÈÉẺẼẸÊẾỀỂỄỆÌÍỈĨỊÒÓỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÙÚỦŨỤƯỨỪỬỮỰỲÝỶỸỴĐ].*");
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String[] tokens = lower.replaceAll("[^a-zàáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ\\s]", " ").trim().split("\\s+");

        Set<String> enMarkers = Set.of(
                "hello", "hi", "hey", "what", "whats", "how", "why", "where", "when", "who", "which",
                "can", "could", "would", "should", "will", "please", "thank", "thanks",
                "help", "tell", "explain", "define", "pronounce", "pronunciation", "mean", "means", "meaning",
                "translate", "translation", "difference", "between", "about", "want", "need", "like", "know",
                "speak", "learn", "learning", "vietnamese", "english", "good", "morning", "afternoon",
                "evening", "night", "much", "many", "price", "cost", "order", "food", "coffee", "drink", "eat",
                "is", "are", "am", "was", "were", "do", "does", "did", "have", "has", "had",
                "my", "your", "his", "her", "our", "their", "this", "that", "these", "those",
                "with", "for", "from", "to", "in", "of", "the", "a", "an", "and", "or", "but",
                "i", "you", "we", "they", "he", "she", "it", "me", "us", "them", "say", "said", "use", "sentence", "example"
        );
        Set<String> viQuestionOrFunctionMarkers = Set.of(
                "xin", "chao", "ban", "toi", "minh", "anh", "chi", "em", "chau", "bac",
                "khong", "duoc", "chua", "nhung", "nhu", "the", "nao", "bao", "nhieu", "tien",
                "lam", "on", "giup", "voi", "nhe", "nha", "vang", "cam", "ngon", "qua",
                "muon", "hoc", "tieng", "viet", "nghia", "la", "gi", "sao", "vay", "ha", "nhi"
        );

        int enCount = 0;
        int viFuncCount = 0;
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            String unacc = removeAccents(tok);
            if (enMarkers.contains(tok)) enCount++;
            if (viQuestionOrFunctionMarkers.contains(unacc) && !enMarkers.contains(tok)) viFuncCount++;
        }

        if (hasViDiacritics) {
            // E.g. "what does đi bơi mean?", "explain đi bơi", "đi bơi meaning" -> enCount >= 1 and dominates
            return enCount >= 1 && enCount >= viFuncCount;
        }
        if (enCount > 0 && enCount >= viFuncCount) return true;
        if (viFuncCount > 0) return false;
        return lower.matches("^[a-z0-9\\s?,!.'\"-]+$");
    }

    private String resolveResponseLanguage(String userText, String requestedLanguage, String userNativeLanguage) {
        if (userText != null && !userText.isBlank()) {
            return isEnglishMessage(userText) ? "English" : "Vietnamese";
        }
        if (requestedLanguage != null && !requestedLanguage.isBlank()) {
            String language = requestedLanguage.trim().toLowerCase(Locale.ROOT);
            if (language.startsWith("en")) return "English";
            if (language.startsWith("vi")) return "Vietnamese";
        }
        return (userNativeLanguage != null && !userNativeLanguage.isBlank()) ? userNativeLanguage : "Vietnamese";
    }

    /**
     * Periodically update conversation working memory / context summary.
     * Ensures long-term facts (e.g. user moved to Hanoi for university) survive beyond the sliding window.
     */
    private void updateContextSummaryIfNeeded(AiSession session, List<AiMessage> previousMessages, String userText) {
        int totalMessages = (previousMessages != null ? previousMessages.size() : 0) + 1;
        boolean shouldUpdate = (totalMessages >= 4) && (session.getContextSummary() == null || session.getTurnsCount() % 4 == 0);
        if (!shouldUpdate) {
            return;
        }

        try {
            // Build recent dialogue for summarization
            StringBuilder historyText = new StringBuilder();
            if (previousMessages != null) {
                int start = Math.max(0, previousMessages.size() - 8);
                for (int i = start; i < previousMessages.size(); i++) {
                    AiMessage m = previousMessages.get(i);
                    historyText.append("user".equalsIgnoreCase(m.getSender()) ? "Người học: " : "AI: ")
                            .append(m.getText()).append("\n");
                }
            }
            historyText.append("Người học: ").append(userText);

            AiSetting settings = getOrInitSettings();
            String model = settings.getActiveModel();
            if (aiProviderService.isApiKeyAvailable(model)) {
                String summaryPrompt = "Dưới đây là đoạn hội thoại giữa người học tiếng Việt và AI:\n"
                        + historyText + "\n\n"
                        + "Hãy tóm tắt ngắn gọn trong 1-2 câu các thông tin cá nhân và ngữ cảnh quan trọng mà người học đã chia sẻ (ví dụ: đang ở đâu, làm gì, lý do chuyển đến, sở thích, trường học, nghề nghiệp...). Không thêm lời dẫn, chỉ ghi thông tin tóm tắt:";
                GeminiApiClient.GeminiResponse res = aiProviderService.generateContent(
                        "Bạn là trợ lý trích xuất ngữ cảnh học viên tiếng Việt.", summaryPrompt, model);
                if (res != null && res.text() != null && !res.text().isBlank()) {
                    session.setContextSummary(res.text().trim());
                    return;
                }
            }

            // Fallback heuristic extraction
            String current = session.getContextSummary() != null ? session.getContextSummary() : "";
            String lower = userText.toLowerCase();
            if (lower.contains("chuyển đến") || lower.contains("ở hà nội") || lower.contains("ở sài gòn") || lower.contains("ở đà nẵng")) {
                current += (current.isEmpty() ? "" : "; ") + "Người học: " + userText.trim();
            } else if (lower.contains("học đại học") || lower.contains("sinh viên") || lower.contains("đi làm") || lower.contains("du lịch")) {
                current += (current.isEmpty() ? "" : "; ") + "Mục đích/Hoạt động: " + userText.trim();
            }
            if (!current.isEmpty()) {
                session.setContextSummary(current);
            }
        } catch (Exception e) {
            log.warn("Could not update context summary for session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Builds true multi-turn contents according to official Google Gemini API specification.
     * Enforces strictly alternating roles: user -> model -> user -> model -> ... -> user.
     * Keeps a sliding window of the most recent turns and ensures contents strictly begins with a user turn.
     */
    private List<Map<String, Object>> buildMultiTurnContents(List<AiMessage> previousMessages, String currentUserText, String preferredLanguage) {
        String langInstruction = "English".equalsIgnoreCase(preferredLanguage)
                ? "\n\n[Instruction: Reply in ENGLISH. Use '**Example:**' (not '**Ví dụ:**'), explain meanings in English, and write your closing follow-up question in English (e.g., 'Have you used this phrase in any sentence? Or would you like me to help with pronunciation?').]"
                : "\n\n[Hướng dẫn: Trả lời hoàn toàn bằng TIẾNG VIỆT. Dùng '**Ví dụ:**' (không dùng '**Example:**') và đặt câu hỏi gợi mở cuối câu bằng tiếng Việt (ví dụ: 'Bạn có dùng cụm từ này trong câu nào không? Hay muốn mình giúp phát âm?').]";
        String effectiveUserText = currentUserText + langInstruction;
        List<Map<String, Object>> contents = new ArrayList<>();

        if (previousMessages != null && !previousMessages.isEmpty()) {
            // Keep the most recent 12 messages for strong contextual memory without bloating tokens
            int windowSize = 12;
            int start = Math.max(0, previousMessages.size() - windowSize);
            String lastRole = null;
            StringBuilder currentText = new StringBuilder();

            for (int i = start; i < previousMessages.size(); i++) {
                AiMessage msg = previousMessages.get(i);
                String text = msg.getText();
                if (text == null || text.isBlank()) continue;

                // Map 'user' -> 'user', 'ai' -> 'model'
                String role = "user".equalsIgnoreCase(msg.getSender()) ? "user" : "model";

                // Compact very long past AI turns slightly to keep token usage optimal
                if ("model".equals(role) && text.length() > 300) {
                    text = text.substring(0, 300) + "...";
                }

                if (role.equals(lastRole)) {
                    // Combine consecutive same-role messages so Gemini never errors on non-alternating turns
                    currentText.append("\n").append(text);
                    contents.get(contents.size() - 1).put("parts", List.of(Map.of("text", currentText.toString())));
                } else {
                    lastRole = role;
                    currentText = new StringBuilder(text);
                    Map<String, Object> turn = new HashMap<>();
                    turn.put("role", role);
                    turn.put("parts", List.of(Map.of("text", text)));
                    contents.add(turn);
                }
            }

            // Gemini API requirement: contents must begin with a user turn
            while (!contents.isEmpty() && "model".equals(contents.get(0).get("role"))) {
                contents.remove(0);
            }

            // Append current user message
            if (!contents.isEmpty() && "user".equals(contents.get(contents.size() - 1).get("role"))) {
                Map<String, Object> lastTurn = contents.get(contents.size() - 1);
                String merged = lastTurn.get("parts") != null && !((List<?>) lastTurn.get("parts")).isEmpty()
                        ? ((Map<?, ?>) ((List<?>) lastTurn.get("parts")).get(0)).get("text") + "\n" + effectiveUserText
                        : effectiveUserText;
                lastTurn.put("parts", List.of(Map.of("text", merged)));
            } else {
                Map<String, Object> currentTurn = new HashMap<>();
                currentTurn.put("role", "user");
                currentTurn.put("parts", List.of(Map.of("text", effectiveUserText)));
                contents.add(currentTurn);
            }
        } else {
            // First turn in a session
            Map<String, Object> currentTurn = new HashMap<>();
            currentTurn.put("role", "user");
            currentTurn.put("parts", List.of(Map.of("text", effectiveUserText)));
            contents.add(currentTurn);
        }

        return contents;
    }

    private String enforceLanguageConsistency(String text, String preferredLanguage) {
        if (text == null || text.isBlank()) return text;
        boolean isEn = "English".equalsIgnoreCase(preferredLanguage);
        if (isEn) {
            return text
                    .replaceAll("(?i)\\*\\*Ví dụ:\\*\\*", "**Example:**")
                    .replaceAll("(?i)\\bVí dụ:", "Example:")
                    .replaceAll("(?i)Bạn có dùng cụm từ này trong câu nào không\\?\\s*Hay muốn (tôi|mình) giúp phát âm\\?",
                            "Have you used this phrase in any sentence? Or would you like me to help with pronunciation?")
                    .replaceAll("(?i)Bạn có dùng từ này trong câu nào không\\?\\s*Hay muốn (tôi|mình) giúp phát âm\\?",
                            "Have you used this word in any sentence? Or would you like me to help with pronunciation?")
                    .replaceAll("(?i)Bạn có muốn thử đặt câu với cụm từ này không\\?",
                            "Would you like to try making a sentence with this phrase?")
                    .replaceAll("(?i)Hay muốn (tôi|mình) giúp phát âm\\?",
                            "Or would you like me to help with pronunciation?");
        } else {
            return text
                    .replaceAll("(?i)\\*\\*Examples?:\\*\\*", "**Ví dụ:**")
                    .replaceAll("(?m)^Examples?:", "Ví dụ:")
                    .replaceAll("(?i)Have you used this (phrase|word) in (any|a) sentence( yet)?\\?\\s*Or would you like( me to)? help with pronunciation\\?",
                            "Bạn có dùng cụm từ này trong câu nào không? Hay muốn mình giúp phát âm?");
        }
    }

    private AiMessage parseAndBuildAiMessage(AiSession session, String reply, String mode) {
        return parseAndBuildAiMessage(session, reply, mode, "Vietnamese");
    }

    private AiMessage parseAndBuildAiMessage(AiSession session, String reply, String mode, String preferredLanguage) {
        String cleaned = reply.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            Map<String, Object> map = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
            String text = enforceLanguageConsistency((String) map.getOrDefault("text", cleaned), preferredLanguage);
            String translation = (String) map.get("translation");
            String explanation = enforceLanguageConsistency((String) map.get("explanation"), preferredLanguage);
            String nativeAlternative = (String) map.get("nativeAlternative");
            String culturalNote = enforceLanguageConsistency((String) map.get("culturalNote"), preferredLanguage);
            String grammarCorrection = enforceLanguageConsistency((String) map.get("grammarCorrection"), preferredLanguage);
            Object savedWordsObj = map.get("savedWords");
            String savedWordsJson = savedWordsObj != null ? objectMapper.writeValueAsString(savedWordsObj) : null;

            return AiMessage.builder()
                    .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                    .session(session)
                    .sender("ai")
                    .name("Hola AI Tutor")
                    .avatar(getModeIcon(mode))
                    .mode(mode)
                    .text(text)
                    .translation(translation)
                    .explanation(explanation)
                    .nativeAlternative(nativeAlternative)
                    .culturalNote(culturalNote)
                    .grammarCheckJson(grammarCorrection)
                    .savedWordsJson(savedWordsJson)
                    .build();
        } catch (Exception e) {
            log.warn("Gemini response is not valid JSON, using direct text: {}", e.getMessage());
            return AiMessage.builder()
                    .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                    .session(session)
                    .sender("ai")
                    .name("Hola AI Tutor")
                    .avatar(getModeIcon(mode))
                    .mode(mode)
                    .text(enforceLanguageConsistency(cleaned, preferredLanguage))
                    .explanation("English".equalsIgnoreCase(preferredLanguage)
                            ? "Smart response in " + mode + " mode"
                            : "Phản hồi thông minh theo chế độ " + getModeTitle(mode))
                    .build();
        }
    }

    private AiMessage generateOfflineTutorResponse(AiSession session, String userText, String mode, String level, ConversationIntent intent, List<AiMessage> previousMessages, String responseLang) {
        String trimmed = userText.trim();
        String lower = trimmed.toLowerCase();
        String unaccented = removeAccents(lower);
        boolean isEn = "English".equalsIgnoreCase(responseLang) || isEnglishMessage(trimmed);

        String reply;
        String explanation = null;
        String translation = null;
        String nativeAlternative = null;
        String culturalNote = null;
        String grammarCorrection = null;
        List<Map<String, String>> words = new ArrayList<>();

        // 0. Check Kinship Pronouns: "đại từ xưng hô", "xưng hô", "anh, chị, em, cô, chú"
        if (unaccented.contains("dai tu xung ho") || unaccented.contains("xung ho") || (unaccented.contains("dai tu") && (unaccented.contains("anh") || unaccented.contains("em") || unaccented.contains("co") || unaccented.contains("chu"))) || (isEn && (lower.contains("pronoun") || lower.contains("address")))) {
            reply = isEn
                    ? "### 🏮 Vietnamese Kinship Pronoun System\n\n" +
                      "Unlike English which relies on generic *'I'* and *'you'*, Vietnamese addresses people like extended family based on relative age and social connection:\n\n" +
                      "| Person You Speak To | Call Them | Refer to Yourself As |\n" +
                      "| :--- | :--- | :--- |\n" +
                      "| **Male slightly older** | **Anh** | **Em** |\n" +
                      "| **Female slightly older** | **Chị** | **Em** |\n" +
                      "| **Someone younger** | **Em** | **Anh / Chị** |\n" +
                      "| **Woman parent's age** | **Cô** (or Bác) | **Cháu** (or Con) |\n" +
                      "| **Man parent's age** | **Chú** (or Bác) | **Cháu** |\n" +
                      "| **Elderly person** | **Ông / Bà** | **Cháu** |\n" +
                      "| **Peers of same age** | **Bạn / Cậu** | **Tôi / Mình / Tớ** |\n\n" +
                      "💡 **Golden Rule:** When shopping, dining, or asking directions, calling the other person **'Anh / Chị'** and referring to yourself as **'Em'** is always considered polite and flattering!"
                    : "### 🏮 Hướng dẫn sử dụng Hệ thống Đại từ Xưng hô tiếng Việt\n\n" +
                      "Hệ thống xưng hô là nét văn hóa đặc sắc và tinh tế bậc nhất của tiếng Việt, nơi mọi người đối xử với nhau ấm áp như người trong một gia đình:\n\n" +
                      "| Đối tượng giao tiếp | Xưng hô với họ | Tự xưng là |\n" +
                      "| :--- | :--- | :--- |\n" +
                      "| Nam lớn tuổi hơn một chút | **Anh** | **Em** |\n" +
                      "| Nữ lớn tuổi hơn một chút | **Chị** | **Em** |\n" +
                      "| Người nhỏ tuổi hơn bạn | **Em** | **Anh / Chị** |\n" +
                      "| Phụ nữ trạc tuổi cha mẹ | **Cô** (hoặc Bác) | **Cháu** (hoặc Con) |\n" +
                      "| Đàn ông trạc tuổi cha mẹ | **Chú** (hoặc Bác) | **Cháu** |\n" +
                      "| Người cao tuổi (ngang ông bà) | **Ông / Bà** | **Cháu** |\n" +
                      "| Bạn bè cùng trang lứa | **Bạn / Cậu** | **Tôi / Mình / Tớ** |\n\n" +
                      "---\n\n" +
                      "💡 **Mẹo vàng giao tiếp thực tế:**\n" +
                      "1. **Khi vào quán xá / hỏi đường:** Luôn chủ động gọi người phục vụ hoặc người đối diện là **'Anh / Chị'** và xưng **'Em'** (ví dụ: *'Anh ơi cho em xem thực đơn với ạ!'*).\n" +
                      "2. **Thêm trợ từ 'ạ':** Đặt từ **'ạ'** ở cuối câu khi nói chuyện với người lớn tuổi hơn (ví dụ: *'Dạ, em cảm ơn chị ạ!'*) để thể hiện sự lễ phép tối đa.";
            nativeAlternative = "Dạ, cho em hỏi anh/chị cần hỗ trợ gì ạ?";
            culturalNote = isEn
                    ? "Vietnamese pronouns show deep respect, warmth, and close interpersonal connection."
                    : "Xưng hô theo thứ bậc gia đình phản ánh văn hóa cộng đồng gắn kết và tôn trọng lễ nghĩa của người Việt.";
            words.add(Map.of("word", "Đại từ xưng hô", "meaning", "Kinship pronouns / forms of address"));
            words.add(Map.of("word", "Kính trên nhường dưới", "meaning", "Respecting elders and caring for youngsters"));
            words.add(Map.of("word", "Dạ / Ạ", "meaning", "Polite particles showing respect to listeners"));
        }
        // 1. Check Age Error: "tôi có 20 tuổi" / "tôi có ... tuổi"
        else if (unaccented.matches(".*(toi|minh|em)\\s+(co|la)\\s+\\d+\\s+tuoi.*") || lower.contains("có 20 tuổi") || lower.contains("có 18 tuổi") || lower.contains("có 25 tuổi")) {
            reply = isEn
                    ? "In Vietnamese, when stating your age, we **do not use 'có' (have)** or **'là' (am/is)**. Instead, place your age directly after the pronoun!"
                    : "Trong tiếng Việt, khi nói về số tuổi, chúng ta **không dùng từ 'có'** (khác với tiếng Anh 'I am...' hay tiếng Pháp 'J'ai...'). Người Việt nói trực tiếp số tuổi ngay sau đại từ!";
            grammarCorrection = isEn
                    ? "Do not use 'có + [number] tuổi'. Correct structure: [Pronoun] + [năm nay] + [age] + tuổi."
                    : "Không dùng 'có + [số] tuổi'. Cấu trúc đúng: [Đại từ] + [năm nay] + [số tuổi] + tuổi.";
            nativeAlternative = "Năm nay tôi " + extractAgeOrFallback(trimmed) + " tuổi ạ.";
            explanation = isEn
                    ? "Example: Instead of 'Tôi có 20 tuổi', say 'Tôi 20 tuổi' or more politely 'Năm nay tôi 20 tuổi ạ'."
                    : "Ví dụ: Thay vì nói 'Tôi có 20 tuổi', bạn hãy nói 'Tôi 20 tuổi' hoặc lịch sự hơn là 'Năm nay tôi 20 tuổi ạ'.";
            words.add(Map.of("word", "Năm nay", "meaning", "This year (used commonly before stating age)"));
            words.add(Map.of("word", "Tuổi", "meaning", "Years old / Age"));
        }
        // 2. Check "bị" vs "được" (Passive Voice with positive/negative emotions)
        else if (lower.contains("bị khen") || lower.contains("bị điểm 10") || lower.contains("bị thưởng") || lower.contains("bị trúng số") || lower.contains("bị tăng lương")) {
            reply = isEn
                    ? "Vietnamese distinguishes between positive and negative passive voice! **'Bị'** is only used for unfortunate or unwanted events. For good news or compliments, always use **'được'**!"
                    : "Người Việt rất tinh tế trong việc thể hiện cảm xúc qua câu bị động! Từ **'bị'** chỉ dùng cho những điều xui xẻo, mất mát hoặc không mong muốn. Đối với tin vui hay điều tích cực, bạn nhất định phải dùng **'được'** nhé!";
            grammarCorrection = isEn
                    ? "Tone mismatch: Replace 'bị' with 'được' for positive outcomes."
                    : "Sai sắc thái biểu cảm: Thay 'bị' bằng 'được' khi nhận điều tích cực.";
            nativeAlternative = trimmed.replace("bị", "được");
            explanation = isEn
                    ? "• 'Được': Positive/fortunate events (được khen, được thưởng).\n• 'Bị': Negative/unfortunate events (bị ốm, bị phạt)."
                    : "• 'Được': Nhận điều may mắn, tốt lành (được khen, được thưởng, được tăng lương).\n• 'Bị': Gặp điều rủi ro, tiêu cực (bị ốm, bị phạt, bị trễ giờ).";
            words.add(Map.of("word", "Được khen", "meaning", "To be praised / complimented (positive)"));
            words.add(Map.of("word", "Bị phạt", "meaning", "To be fined / penalized (negative)"));
        }
        // 3. Check Adjective before Noun (Word order error)
        else if (lower.contains("đỏ xe") || lower.contains("to nhà") || lower.contains("đẹp áo") || lower.contains("ngon phở") || lower.contains("lớn trường")) {
            reply = isEn
                    ? "In Vietnamese grammar, **Adjectives always come AFTER the Noun** they modify—the opposite of English (*red car* -> *xe đỏ*)."
                    : "Trong ngữ pháp tiếng Việt, **Tính từ luôn đứng SAU Danh từ** để bổ nghĩa cho danh từ đó. Điều này ngược lại với tiếng Anh (*red car* -> *xe đỏ*).";
            grammarCorrection = isEn
                    ? "Word order: Descriptive adjectives must follow the noun."
                    : "Trật tự từ sai: Tính từ bổ nghĩa phải đứng sau danh từ.";
            nativeAlternative = fixAdjectiveOrder(trimmed);
            explanation = isEn
                    ? "Core rule: [Noun] + [Adjective]. E.g., 'xe đỏ' (red car), 'nhà to' (big house), 'phở ngon' (delicious pho)."
                    : "Quy tắc cốt lõi: [Danh từ] + [Tính từ]. Ví dụ: 'xe đỏ', 'nhà to', 'áo đẹp', 'phở ngon'.";
            words.add(Map.of("word", "Xe đỏ", "meaning", "Red vehicle (Noun + Adjective)"));
            words.add(Map.of("word", "Áo đẹp", "meaning", "Beautiful shirt / clothing"));
        }
        // 4. Redundant pronoun "nó"
        else if (lower.contains("ăn nó ngon") || lower.contains("uống nó ngon") || lower.contains("mua nó rẻ")) {
            reply = isEn
                    ? "Vietnamese naturally **omits object pronouns** ('it' / 'nó') when the object was already mentioned in the first clause. Repeating 'nó' sounds unnatural."
                    : "Tiếng Việt có đặc điểm rất thú vị là thường **tỉnh lược tân ngữ** khi đối tượng đã được nhắc đến ở vế trước. Việc lặp lại từ 'nó' sẽ làm câu nghe hơi cứng nhắc và gượng gạo.";
            grammarCorrection = isEn
                    ? "Omit redundant object pronoun 'nó'."
                    : "Lược bỏ đại từ tân ngữ 'nó' không cần thiết.";
            nativeAlternative = trimmed.replace("ăn nó ngon", "ăn rất ngon").replace("uống nó ngon", "uống rất ngon");
            explanation = isEn
                    ? "Native speakers say: 'Quả chuối này ngọt lắm, ăn rất ngon!' instead of 'ăn nó ngon'."
                    : "Người bản xứ nói: 'Quả chuối này ngọt lắm, ăn rất ngon!' thay vì 'ăn nó ngon'.";
            words.add(Map.of("word", "Ngon lắm", "meaning", "Very delicious (natural spoken tone)"));
        }
        // 5. Tone check: "hỏi" vs "ngã"
        else if (unaccented.contains("dau hoi") || unaccented.contains("dau nga") || unaccented.contains("thanh dieu") || (lower.contains("sữa") && lower.contains("sửa"))) {
            reply = isEn
                    ? "Distinguishing **Dấu hỏi (?)** and **Dấu ngã (~)** is key in Vietnamese pronunciation:\n\n" +
                      "• **Dấu hỏi (?)**: Dips down smoothly and rises gently (e.g., *'sửa xe'*, *'nghỉ ngơi'*).\n" +
                      "• **Dấu ngã (~)**: Dips, breaks with a brief glottal stop in the middle, then rises sharply (e.g., *'sữa tươi'*, *'suy nghĩ'*)."
                    : "Phân biệt dấu hỏi (?) và dấu ngã (~) là một trong những nét duyên dáng nhất của tiếng Việt:\n\n" +
                      "• **Dấu hỏi (?)**: Âm điệu trầm xuống rồi lượn nhẹ lên (như đang thắc mắc: *'sửa xe'*, *'nghỉ ngơi'*, *'quả ổi'*).\n" +
                      "• **Dấu ngã (~)**: Âm điệu gãy gập ở giữa rồi vút cao lên (như bị nghẹn lại một nhịp: *'sữa tươi'*, *'suy nghĩ'*, *'ngã giá'*).";
            explanation = isEn
                    ? "Dialect tip: Southern and Central speakers often pronounce dấu ngã similarly to dấu hỏi in casual speech, though spelling remains distinct!"
                    : "Mẹo phương ngữ: Người miền Nam và miền Trung thường phát âm dấu ngã tương tự dấu hỏi, nhưng khi viết văn bản chuẩn thì vẫn phân biệt rõ rệt!";
            words.add(Map.of("word", "Sữa (dấu ngã)", "meaning", "Milk"));
            words.add(Map.of("word", "Sửa (dấu hỏi)", "meaning", "To fix / repair"));
        }
        // 6. Food & Drink: Phở, Cà phê, Bánh mì
        else if (unaccented.contains("ca phe") || unaccented.contains("coffee")) {
            reply = isEn
                    ? "Vietnamese coffee culture is rich and iconic! Here are the most popular ways to order coffee like a local:\n\n" +
                      "1. **Cà phê sữa đá** (called *'Nâu đá'* in Hanoi): Strong phin-filter coffee with sweet condensed milk over ice.\n" +
                      "2. **Cà phê đen đá**: Bold iced black coffee (you can ask for *'ít đường'* = less sugar).\n" +
                      "3. **Bạc xỉu**: Sweet milk with a light splash of coffee—great if you prefer a milder taste.\n" +
                      "4. **Cà phê trứng**: Hanoi's famous egg coffee topped with creamy whipped egg yolk."
                    : "Văn hóa cà phê Việt Nam cực kỳ phong phú và đậm đà! Khi vào quán, đây là những cách gọi cà phê chuẩn gu người Việt:\n\n" +
                      "1. **Cà phê sữa đá** (Miền Bắc hay gọi là *'Nâu đá'*): Cà phê phin đậm vị pha cùng sữa đặc ngọt béo.\n" +
                      "2. **Cà phê đen đá**: Cà phê nguyên chất, có thể dặn thêm ít đường hoặc không đường.\n" +
                      "3. **Bạc xỉu**: Dành cho người thích ngọt và ít vị đắng (nhiều sữa tươi + sữa đặc, chút xíu cà phê).\n" +
                      "4. **Cà phê trứng**: Đặc sản nổi tiếng của Hà Nội với lớp kem trứng béo ngậy đánh bông.";
            nativeAlternative = "Cho mình một ly cà phê sữa đá ít ngọt nhé!";
            culturalNote = isEn
                    ? "Locals love sitting on low sidewalk stools sipping coffee and chatting with friends for hours."
                    : "Người Việt thường ngồi cà phê vỉa hè để ngắm phố phường hoặc trò chuyện cùng bạn bè hàng giờ đồng hồ.";
            words.add(Map.of("word", "Cà phê sữa đá", "meaning", "Vietnamese iced coffee with condensed milk"));
            words.add(Map.of("word", "Bạc xỉu", "meaning", "Sweet milk with a splash of coffee"));
            words.add(Map.of("word", "Ít ngọt / Ít đường", "meaning", "Less sweet / less sugar"));
        }
        else if (unaccented.contains("pho") || unaccented.contains("banh mi")) {
            reply = isEn
                    ? "Vietnamese street food is unforgettable! Here are natural phrases you can use to order like a local:\n\n" +
                      "• Ordering Phở: *'Cho cháu một bát phở tái nạm gầu, ít hành, kèm đĩa quẩy giòn ạ!'* (One bowl of rare & brisket beef pho, less scallions, with crispy dough sticks please!)\n" +
                      "• Ordering Bánh mì: *'Bán cho em một ổ bánh mì thập cẩm, không cay, nhiều dưa chuột nhé chị!'* (One mixed bánh mì, not spicy, extra cucumber please!)"
                    : "Ẩm thực đường phố Việt Nam luôn khiến thực khách say đắm! Để gọi món ngon đúng ý, bạn hãy lưu lại các mẫu câu này nhé:\n\n" +
                      "• Khi ăn Phở: *'Cho cháu một bát phở tái nạm gầu, ít hành, kèm đĩa quẩy giòn ạ!'*\n" +
                      "• Khi mua Bánh mì: *'Bán cho em một ổ bánh mì thập cẩm, không cay, nhiều dưa chuột nhé chị!'*";
            nativeAlternative = "Cho em một bát phở bò chín, nước trong, ít hành nhé ạ!";
            culturalNote = isEn
                    ? "In Northern Vietnam, phở is served with crispy dough sticks (quẩy) and garlic vinegar; in the South, it comes with hoisin/chili sauce and a generous basket of fresh herbs."
                    : "Ở miền Bắc, phở thường ăn kèm quẩy giòn và giấm tỏi; trong khi ở miền Nam phở ăn kèm tương đen, tương đỏ và rất nhiều loại rau thơm (húng quế, ngò gai).";
            words.add(Map.of("word", "Phở tái chín", "meaning", "Pho with rare and well-done beef"));
            words.add(Map.of("word", "Bánh mì thập cẩm", "meaning", "Combo Vietnamese baguette with pate, meats & herbs"));
        }
        // 7. Shopping & Bargaining: Giá cả, Bao nhiêu tiền
        else if (unaccented.contains("bao nhieu") || unaccented.contains("tra gia") || unaccented.contains("mac ca") || unaccented.contains("gia tien") || unaccented.contains("how much")) {
            reply = isEn
                    ? "When shopping at traditional Vietnamese markets, here is how to ask for prices and bargain politely:\n\n" +
                      "1. **Ask the price:** *'Dạ cô/chị ơi, cái này giá bao nhiêu một cái ạ?'* (Excuse me, how much is this per item?)\n" +
                      "2. **Bargain warmly:** *'Cô bớt cho cháu một chút lấy may nhé!'* (Could you give me a small discount for good luck?)\n" +
                      "3. **Close the deal:** *'150 nghìn được không cô, cháu lấy hai cái luôn ạ!'* (Is 150k okay? I'll take two!)"
                    : "Khi đi chợ truyền thống hoặc mua sắm tại Việt Nam, để hỏi giá và mặc cả vừa lịch sự vừa được lòng người bán, bạn có thể áp dụng công thức sau:\n\n" +
                      "1. **Hỏi giá:** *'Dạ cô/chị ơi, cái này giá bao nhiêu một cái/kg ạ?'*\n" +
                      "2. **Thương lượng nhẹ nhàng:** *'Cô bớt cho cháu một chút lấy may nhé! Cháu mở hàng cho cô nha!'*\n" +
                      "3. **Chốt giá:** *'150 nghìn được không cô, cháu lấy hai cái luôn ạ!'*";
            nativeAlternative = "Dạ cái này bao nhiêu tiền một cái hả chị? Bớt chút cho em mở hàng may mắn nhé!";
            culturalNote = isEn
                    ? "'Mở hàng' (first sale of the day): Vietnamese vendors value a smooth, cheerful first purchase in the morning as a sign of good luck for the whole day."
                    : "Quan niệm 'Mở hàng': Người buôn bán Việt Nam rất coi trọng vị khách đầu tiên trong ngày. Mua bán nhanh nhẹn, xởi lởi vào sáng sớm sẽ mang lại may mắn cho cả ngày!";
            words.add(Map.of("word", "Bao nhiêu tiền?", "meaning", "How much does it cost?"));
            words.add(Map.of("word", "Bớt một chút", "meaning", "Give a small discount please"));
            words.add(Map.of("word", "Mở hàng", "meaning", "First purchase of the day (brings luck)"));
        }
        // 8. Directions & Transport: Hỏi đường, Taxi, Xe ôm
        else if (unaccented.contains("o dau") || unaccented.contains("hoi duong") || unaccented.contains("xe om") || unaccented.contains("taxi") || unaccented.contains("san bay")) {
            reply = isEn
                    ? "When asking for directions or taking transport in Vietnam, starting with a polite form of address makes locals eager to help:\n\n" +
                      "• *'Bác ơi cho cháu hỏi, đường ra Hồ Gươm đi hướng nào ạ?'* (Excuse me, which way is it to Hoan Kiem Lake?)\n" +
                      "• *'Từ đây đến sân bay hết khoảng bao nhiêu phút ạ?'* (How many minutes does it take to get to the airport from here?)\n" +
                      "• *'Bác tài ơi, chở em đến địa chỉ này nhé!'* (Driver, please take me to this address!)"
                    : "Khi cần hỏi đường tại Việt Nam, người dân luôn rất nhiệt tình chỉ dẫn nếu bạn mở đầu bằng lời chào kính ngữ:\n\n" +
                      "• *'Bác ơi cho cháu hỏi, đường ra Hồ Gươm / Chợ Bến Thành đi hướng nào ạ?'*\n" +
                      "• *'Từ đây đến sân bay hết khoảng bao nhiêu phút đi xe máy ạ?'*\n" +
                      "• *'Bác tài ơi, chở em đến địa chỉ này nhé, bật đồng hồ taxi giúp em với ạ!'*";
            nativeAlternative = "Làm ơn cho tôi hỏi đường ra ga tàu đi lối nào ạ?";
            explanation = isEn
                    ? "Addressing someone with 'Bác ơi / Chú ơi / Chị ơi' before asking shows warmth and respect."
                    : "Dùng 'Bác ơi / Chú ơi / Chị ơi' trước câu hỏi luôn khiến người nghe cảm thấy được tôn trọng và sẵn lòng giúp đỡ bạn ngay lập tức.";
            words.add(Map.of("word", "Đi hướng nào?", "meaning", "Which direction should I go?"));
            words.add(Map.of("word", "Làm ơn cho tôi hỏi...", "meaning", "Excuse me, may I ask..."));
        }
        // 9. Greetings & Politeness
        else if (unaccented.matches("^(xin chao|chao|hello|hi|hey|alo).*") || trimmed.equalsIgnoreCase("chào") || trimmed.equalsIgnoreCase("xin chào")) {
            reply = isEn
                    ? "Hello there! 😊 Great to see you today. How is your day going, and what would you like to practice or talk about?"
                    : "Chào bạn nhé! 😊 Rất vui được gặp lại bạn. Hôm nay của bạn thế nào rồi?";
        }
        // 10. Regional Vietnamese differences
        else if (unaccented.contains("thiet") || unaccented.contains("muong") || unaccented.contains("thia") || unaccented.contains("chen") || unaccented.contains("bat") || unaccented.contains("bac nam") || unaccented.contains("mien trung")) {
            reply = isEn
                    ? "Northern, Central, and Southern Vietnamese have fascinating vocabulary differences:\n\n" +
                      "| Concept | Northern | Southern | Central |\n" +
                      "| :--- | :--- | :--- | :--- |\n" +
                      "| **Spoon** | Thìa | Muỗng | Muỗng / Thìa |\n" +
                      "| **Small bowl** | Bát | Chén | Chén / Bát |\n" +
                      "| **Pork** | Thịt lợn | Thịt heo | Thịt heo |\n" +
                      "| **Pineapple** | Quả dứa | Trái thơm / Khóm | Quả thơm |\n" +
                      "| **Umbrella** | Cái ô | Cây dù | Cây dù |"
                    : "Tiếng Việt 3 miền Bắc - Trung - Nam có những sự khác biệt từ vựng cực kỳ thú vị:\n\n" +
                      "| Đồ vật / Khái niệm | Miền Bắc | Miền Nam | Miền Trung |\n" +
                      "| :--- | :--- | :--- | :--- |\n" +
                      "| **Thìa ăn cơm** | Thìa | Muỗng | Muỗng / Thìa |\n" +
                      "| **Đồ đựng cơm** | Bát | Chén | Chén / Bát |\n" +
                      "| **Thịt heo/lợn** | Thịt lợn | Thịt heo | Thịt heo |\n" +
                      "| **Quả dứa** | Quả dứa | Trái thơm / Khóm | Quả thơm |\n" +
                      "| **Cái ô** | Cái ô | Cây dù | Cây dù |";
            culturalNote = isEn
                    ? "Even though regional words vary slightly, Vietnamese speakers across all three regions understand each other easily!"
                    : "Dù từ vựng có khác nhau đôi chút nhưng người Việt ở cả 3 miền đều hiểu trọn vẹn ngữ cảnh của bạn. Đừng ngại sử dụng nhé!";
            words.add(Map.of("word", "Thìa (Bắc) = Muỗng (Nam)", "meaning", "Spoon"));
            words.add(Map.of("word", "Bát (Bắc) = Chén (Nam)", "meaning", "Small bowl"));
        }
        // 11. Mode specific contextual fallbacks
        else {
            switch (mode.toLowerCase()) {
                case "grammar":
                    reply = isEn
                            ? "### 📝 Grammar & Sentence Review:\n\n" +
                              "> *\"" + trimmed + "\"*\n\n" +
                              "**Feedback:** Your message communicates the main idea clearly! To sound even more like a native Vietnamese speaker, keep these tips in mind:\n\n" +
                              "1. **Word Order:** `[Subject] + [Verb] + [Object]`, with adjectives placed *after* nouns.\n" +
                              "2. **Ending Particles:** Add friendly particles like *'nhé'*, *'nha'*, or polite *'ạ'* at the end of sentences.\n" +
                              "3. **Pronoun Omission:** Drop redundant pronouns when the context is already clear."
                            : "### 📝 Phân tích ngữ pháp câu của bạn:\n\n" +
                              "> *\"" + trimmed + "\"*\n\n" +
                              "**Nhận xét:** Câu của bạn đã truyền đạt được ý nghĩa cơ bản. Để câu văn tự nhiên và chuẩn phong thái người bản xứ, hãy ghi nhớ:\n\n" +
                              "1. **Trật tự từ:** `[Chủ ngữ] + [Vị ngữ] + [Tân ngữ]`, tính từ đứng sau danh từ.\n" +
                              "2. **Trợ từ tình thái:** Thêm các từ như *'nhé'*, *'nha'*, *'ạ'* vào cuối câu để tạo sắc thái lịch sự, mềm mại.\n" +
                              "3. **Tỉnh lược:** Lược bớt các đại từ sở hữu hay tân ngữ thừa nếu ngữ cảnh đã rõ ràng.";
                    nativeAlternative = trimmed.endsWith("ạ") || trimmed.endsWith("nhé") ? trimmed : (trimmed + " nhé ạ!");
                    explanation = isEn
                            ? "Adding ending particles makes spoken Vietnamese sound much warmer and more natural."
                            : "Thêm trợ từ cuối câu giúp lời nói tự nhiên và gần gũi hơn rất nhiều.";
                    words.add(Map.of("word", "Nhé / Nha", "meaning", "Friendly sentence-ending particle"));
                    break;

                case "translation":
                    reply = isEn
                            ? "### 🌐 Contextual Translation:\n\n" +
                              "**Source text:** *\"" + trimmed + "\"*\n\n" +
                              "When translating into natural Vietnamese, native speakers focus on **relationship pronouns** and **polite ending particles** (*nhé, ạ*) rather than word-for-word translation."
                            : "### 🌐 Dịch thuật & Chuyển ngữ tự nhiên:\n\n" +
                              "**Ngữ cảnh câu:** *\"" + trimmed + "\"*\n\n" +
                              "Khi dịch sang tiếng Việt tự nhiên, người bản xứ thường chú trọng **sắc thái biểu cảm** và vai vế xưng hô thay vì dịch từng từ theo kiểu máy móc (word-by-word).\n\n" +
                              "💡 **Gợi ý diễn đạt:** Diễn đạt ngắn gọn, kèm đại từ phù hợp với hoàn cảnh giao tiếp hàng ngày.";
                    translation = "Natural contextual translation for: " + trimmed;
                    explanation = isEn
                            ? "Choose the right pronoun (tôi, mình, em, anh/chị) depending on formality and age."
                            : "Chú ý ngữ cảnh trang trọng hay thân mật để chọn đại từ (tôi, em, mình, anh) cho chuẩn xác.";
                    break;

                case "culture_guide":
                    reply = isEn
                            ? "### 🏮 Vietnamese Cultural Etiquette:\n\n" +
                              "Vietnamese culture deeply values **harmony, family bonds, and respect for elders** (*kính trên nhường dưới*).\n\n" +
                              "• Always remove your shoes before entering a Vietnamese home.\n" +
                              "• At family meals, younger people invite elders to eat first: *'Con mời bố mẹ ăn cơm ạ!'*\n" +
                              "• Use **both hands** when giving or receiving items from someone older."
                            : "### 🏮 Nét đẹp văn hóa Việt Nam:\n\n" +
                              "Về nội dung bạn vừa chia sẻ: Văn hóa người Việt rất coi trọng **sự hòa nhã, tình cảm gia đình và kính trên nhường dưới**.\n\n" +
                              "• Khi vào nhà người Việt, bạn nên cởi giày dép để bên ngoài.\n" +
                              "• Trong bữa ăn gia đình, người nhỏ tuổi mời người lớn tuổi trước khi cầm đũa: *'Con mời bố mẹ xới cơm ạ!'*.\n" +
                              "• Khi nhận hoặc trao đồ vật cho người lớn, hãy dùng **hai tay** để thể hiện sự kính trọng.";
                    culturalNote = isEn
                            ? "'Lời chào cao hơn mâm cỗ' — A warm, respectful greeting is valued more than a grand feast."
                            : "Lời chào cao hơn mâm cỗ: Sự lễ phép và chào hỏi đúng mực luôn chiếm trọn cảm tình của người Việt.";
                    break;

                case "travel":
                    reply = isEn
                            ? "### ✈️ Essential Vietnam Travel Phrases:\n\n" +
                              "Here are handy survival phrases for your trip in Vietnam:\n\n" +
                              "• *'Làm ơn cho tôi xem thực đơn được không ạ?'* (May I see the menu, please?)\n" +
                              "• *'Món này có cay không? Cho tôi ít cay nhé!'* (Is this spicy? Less spicy for me, please!)\n" +
                              "• *'Cho tôi xin hóa đơn thanh toán nhé!'* (Could I have the bill, please?)"
                            : "### ✈️ Cẩm nang du lịch & Sinh tồn:\n\n" +
                              "Để chuyến du lịch Việt Nam của bạn suôn sẻ và đáng nhớ, hãy trang bị ngay các câu nói thông dụng này:\n\n" +
                              "• *'Làm ơn cho tôi xem thực đơn có hình ảnh được không ạ?'*\n" +
                              "• *'Món này có cay không? Tôi ăn ít cay / không ăn cay được ạ!'*\n" +
                              "• *'Cho tôi xin hóa đơn thanh toán nhé!'*";
                    nativeAlternative = "Làm ơn cho em xin hóa đơn thanh toán nhé ạ!";
                    explanation = isEn
                            ? "Starting with 'Làm ơn...' or ending with '...ạ' is the most polite way to make requests."
                            : "Dùng 'Làm ơn...' ở đầu câu hoặc '...ạ' ở cuối câu là cách nói lịch sự nhất trong ngành dịch vụ du lịch.";
                    break;

                case "regional":
                    reply = isEn
                            ? "### 🗺️ Vietnamese Across 3 Regions:\n\n" +
                              "Vietnamese dialects paint a vibrant cultural picture:\n" +
                              "• **Northern (Hanoi):** Clear 6-tone distinction, formal and standard phrasing.\n" +
                              "• **Central (Hue, Da Nang):** Warm, deep intonation with unique local words (*mô, tê, răng, rứa*).\n" +
                              "• **Southern (Saigon):** Melodic, relaxed delivery where *dấu ngã* merges toward *dấu hỏi*."
                            : "### 🗺️ Tiếng Việt 3 miền:\n\n" +
                              "Ngôn ngữ Việt Nam như một bức tranh đa sắc màu:\n" +
                              "• Miền Bắc: Chuẩn phát âm rõ thanh điệu, ngữ điệu trang trọng, từ ngữ khuôn mẫu.\n" +
                              "• Miền Trung: Giọng nói mộc mạc, âm vực trầm ấm, nhiều từ cổ đặc trưng (*mô, tê, răng, rứa*).\n" +
                              "• Miền Nam: Giọng nói ngọt ngào, mềm mại, thường lướt dấu ngã thành dấu hỏi, cách xưng hô cởi mở (*chế, hai, út, tía*).";
                    culturalNote = isEn
                            ? "No matter which region you visit, a friendly smile and polite greeting will always connect you with locals!"
                            : "Dù ở vùng miền nào, sự chân tình và nụ cười luôn là cầu nối ngôn ngữ tuyệt vời nhất!";
                    break;

                default: // tutor, conversation
                    if (intent == ConversationIntent.PRONUNCIATION) {
                        reply = isEn
                                ? "To pronounce this clearly in Vietnamese, pay close attention to the vowel shape and tone contour. Try saying it out loud once—how does it feel?"
                                : "Để phát âm rõ ràng từ này, bạn hãy chú ý giữ khẩu hình tròn và nhả chữ dứt khoát nhé. Bạn thử đọc lại một lần xem sao nào!";
                    } else if (intent == ConversationIntent.VOCABULARY) {
                        reply = isEn
                                ? "This expression is very common in everyday Vietnamese conversation! Would you like to see a few real-life example sentences with it?"
                                : "Từ này thường được người Việt dùng rất nhiều trong giao tiếp hàng ngày. Bạn muốn nghe một câu ví dụ thực tế không?";
                    } else if (intent == ConversationIntent.PRACTICE) {
                        reply = isEn
                                ? "Awesome, let's practice together! Try making a short sentence using this phrase, and I'll help you refine it."
                                : "Tuyệt vời, cùng luyện tập nhé! Bạn thử đặt một câu ngắn sử dụng từ này xem sao nào?";
                    } else if (intent == ConversationIntent.TRANSLATION) {
                        reply = isEn
                                ? "In natural spoken Vietnamese, native speakers keep this short and add a friendly ending particle like *'nhé'* or *'ạ'* at the end!"
                                : "Trong tiếng Việt giao tiếp tự nhiên, câu này người bản xứ thường nói ngắn gọn kèm trợ từ tình thái ở cuối cho thân thiện nhé!";
                    } else {
                        // CASUAL_CHAT with multi-turn context retention
                        String historyConcat = "";
                        if (previousMessages != null) {
                            historyConcat = previousMessages.stream()
                                    .map(AiMessage::getText)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining(" "))
                                    .toLowerCase();
                        }
                        if (unaccented.contains("chuyen den ha noi") || (unaccented.contains("ha noi") && unaccented.contains("moi den")) || (isEn && lower.contains("hanoi"))) {
                            reply = isEn
                                    ? "Oh, welcome to Hanoi! 😄 Are you in Hanoi for study, work, or travel?"
                                    : "Ồ, bạn mới chuyển đến Hà Nội à! Chào mừng bạn đến với thủ đô nhé 😄 Bạn chuyển đến để học hay đi làm vậy?";
                        } else if (unaccented.contains("hoc dai hoc") || unaccented.contains("sinh vien") || unaccented.contains("truong dai hoc") || (isEn && (lower.contains("university") || lower.contains("student")))) {
                            if (historyConcat.contains("hà nội") || historyConcat.contains("ha noi") || historyConcat.contains("hanoi")) {
                                reply = isEn
                                        ? "Starting university life in Hanoi sounds exciting! 😄 Which university are you attending, and have you gotten used to life there yet?"
                                        : "Vậy là bạn đang bắt đầu một hành trình mới rồi 😄 Bạn học trường nào thế? Đã quen với cuộc sống sinh viên ở Hà Nội chưa?";
                            } else {
                                reply = isEn
                                        ? "That's wonderful! 😄 What major are you studying right now?"
                                        : "Tuyệt vời, vậy là bạn đang bắt đầu quãng đời sinh viên rồi 😄 Bạn đang theo học ngành gì thế?";
                            }
                        } else if (unaccented.contains("di lam") || unaccented.contains("cong viec") || unaccented.contains("dong nghiep") || (isEn && (lower.contains("work") || lower.contains("job")))) {
                            reply = isEn
                                    ? "Starting work in a new environment is always an adventure! Do you often get a chance to speak Vietnamese with your colleagues?"
                                    : "Bắt đầu công việc ở một môi trường mới chắc hẳn có nhiều điều thú vị! Ở chỗ làm bạn có thường xuyên giao tiếp bằng tiếng Việt không?";
                        } else if (unaccented.contains("chua quen") || unaccented.contains("thoi tiet") || unaccented.contains("khi hau") || (isEn && lower.contains("weather"))) {
                            reply = isEn
                                    ? "It always takes a little time to adjust to a new place and weather! How do you find the pace of life compared to back home?"
                                    : "Mới chuyển đến nơi ở mới bao giờ cũng cần chút thời gian để thích nghi nè. Bạn thấy nhịp sống ở đây có khác nhiều so với quê nhà không?";
                        } else if (unaccented.contains("thich an") || unaccented.contains("an uong") || unaccented.contains("mon an") || (isEn && lower.contains("food"))) {
                            reply = isEn
                                    ? "Vietnamese cuisine is so diverse and delicious! Have you tried phở bò or bún chả yet? How did you like the flavors?"
                                    : "Ẩm thực Việt Nam ngon và phong phú lắm đấy! Bạn đã có dịp thử phở bò hay bún chả chưa? Thấy khẩu vị thế nào?";
                        } else {
                            reply = isEn
                                    ? "That sounds really interesting! 😊 Could you tell me a bit more about it? I'm happy to help you express it in natural Vietnamese too!"
                                    : "Nghe thú vị thật đấy! Bạn có thể chia sẻ thêm cho mình về điều này không? 😊";
                        }
                    }
                    break;
            }
        }

        String savedWordsJson = null;
        if (!words.isEmpty()) {
            try {
                savedWordsJson = objectMapper.writeValueAsString(words);
            } catch (Exception ignored) {}
        }

        return AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("ai")
                .name("Hola AI Tutor")
                .avatar(getModeIcon(mode))
                .mode(mode)
                .text(reply)
                .translation(translation)
                .explanation(explanation)
                .nativeAlternative(nativeAlternative)
                .culturalNote(culturalNote)
                .grammarCheckJson(grammarCorrection)
                .savedWordsJson(savedWordsJson)
                .build();
    }

    private String removeAccents(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d").replace("Đ", "D");
    }

    private String extractAgeOrFallback(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(text);
        return m.find() ? m.group() : "20";
    }

    private String fixAdjectiveOrder(String text) {
        return text.replace("đỏ xe", "xe đỏ")
                .replace("to nhà", "nhà to")
                .replace("đẹp áo", "áo đẹp")
                .replace("ngon phở", "phở ngon")
                .replace("lớn trường", "trường lớn");
    }

    private String generateInitialGreetingText(AiSession session, User user, AiSetting settings) {
        String learnerName = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName() : "bạn";
        String level = user.getTargetLevel() != null ? user.getTargetLevel() : "A1";
        String language = user.getNativeLanguage() != null ? user.getNativeLanguage() : "English";
        String goal = user.getLearningGoal() != null ? user.getLearningGoal() : "Everyday conversations";
        String prompt = "Start a natural, friendly conversation with learner " + learnerName
                + ". This is a " + getModeTitle(session.getMode()) + " session."
                + " Do not give a long introduction or a list of choices."
                + " Say hello, suggest one open topic related to the learner goal, and ask only one follow-up question."
                + " Learner level: " + level + ". Preferred explanation language: " + language
                + ". Learning goal: " + goal + ". Return only the JSON schema from the system prompt.";

        if (aiProviderService.isApiKeyAvailable(settings.getActiveModel())) {
            GeminiApiClient.GeminiResponse response = aiProviderService.generateContent(
                    buildSystemPrompt(session.getMode(), level, language, goal,
                            ConversationIntent.CASUAL_CHAT, null),
                    prompt, settings.getActiveModel());
            if (response != null && response.text() != null && !response.text().isBlank()) {
                AiMessage generated = parseAndBuildAiMessage(session, response.text(), session.getMode());
                if (generated.getText() != null && !generated.getText().isBlank()) {
                    return generated.getText();
                }
            }
        }

        return "Chào " + learnerName + "! Mình ở đây để đồng hành cùng bạn. Bạn muốn bắt đầu bằng một câu chuyện ngắn về ngày hôm nay không?";
    }

    private String getInitialGreetingForMode(String mode, User user) {
        String name = user.getFullName() != null ? user.getFullName() : "bạn";
        return switch (mode.toLowerCase()) {
            case "grammar" -> "Chào " + name + "! Hãy nhập bất kỳ câu tiếng Việt nào bạn muốn kiểm tra, mình sẽ giúp bạn sửa lỗi và hoàn thiện câu văn nhé! 📝";
            case "translation" -> "Chào " + name + "! Nhập từ hoặc câu tiếng Việt/tiếng Anh cần dịch, mình sẽ dịch sát ngữ cảnh và kèm giải thích! 🌐";
            case "conversation" -> "Chào " + name + "! Hôm nay bạn muốn trò chuyện về chủ đề gì nào? Thời tiết, sở thích hay ẩm thực Việt Nam? 💬";
            case "culture_guide" -> "Chào " + name + "! Khám phá nét đẹp văn hóa và phong tục tập quán Việt Nam cùng mình nhé! 🏮";
            case "travel" -> "Chào " + name + "! Cần hỗ trợ tình huống du lịch sinh tồn nào: gọi món, hỏi đường hay đón xe? ✈️";
            case "regional" -> "Chào " + name + "! Tìm hiểu sự khác biệt thú vị giữa tiếng Việt miền Bắc, miền Trung và miền Nam nhé! 🇻🇳";
            default -> "Chào " + name + "! Mình là Hola AI Tutor. Bạn có thắc mắc gì về tiếng Việt hôm nay không? 🎓";
        };
    }

    private String getModeTitle(String mode) {
        return switch (mode.toLowerCase()) {
            case "grammar" -> "Ngữ pháp & Sửa câu";
            case "translation" -> "Dịch thuật ngữ cảnh";
            case "conversation" -> "Luyện hội thoại";
            case "culture_guide" -> "Chỉ dẫn văn hóa";
            case "travel" -> "Du lịch sinh tồn";
            case "regional" -> "Tiếng Việt 3 miền";
            default -> "Gia sư toàn diện";
        };
    }

    private String getModeIcon(String mode) {
        return switch (mode.toLowerCase()) {
            case "grammar" -> "📝";
            case "translation" -> "🌐";
            case "conversation" -> "💬";
            case "culture_guide" -> "🏮";
            case "travel" -> "✈️";
            case "regional" -> "🇻🇳";
            default -> "🎓";
        };
    }

    public AiSessionResponse toSessionResponse(AiSession session, List<AiMessage> messages) {
        List<AiMessageResponse> msgResponses = messages != null
                ? messages.stream().map(this::toMessageResponse).collect(Collectors.toList())
                : null;

        return AiSessionResponse.builder()
                .id(session.getId())
                .userId(session.getUser() != null ? session.getUser().getId() : null)
                .mode(session.getMode())
                .title(session.getTitle())
                .scenarioId(session.getScenarioId())
                .status(session.getStatus())
                .turnsCount(session.getTurnsCount())
                .score(session.getScore())
                .feedbackSummary(session.getFeedbackSummary())
                .contextSummary(session.getContextSummary())
                .messages(msgResponses)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .endedAt(session.getEndedAt())
                .build();
    }

    public AiMessageResponse toMessageResponse(AiMessage message) {
        List<Map<String, String>> words = null;
        if (message.getSavedWordsJson() != null && !message.getSavedWordsJson().isBlank()) {
            try {
                words = objectMapper.readValue(message.getSavedWordsJson(), new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception ignored) {}
        }

        return AiMessageResponse.builder()
                .id(message.getId())
                .sender(message.getSender())
                .name(message.getName())
                .avatar(message.getAvatar())
                .text(message.getText())
                .translation(message.getTranslation())
                .explanation(message.getExplanation())
                .nativeAlternative(message.getNativeAlternative())
                .culturalNote(message.getCulturalNote())
                .grammarCorrection(message.getGrammarCorrection())
                .feedbackJson(message.getFeedbackJson())
                .savedWords(words)
                .timestamp(message.getCreatedAt())
                .build();
    }

    public AiReviewResponse toReviewResponse(AiReview review) {
        List<String> tags = Collections.emptyList();
        try {
            if (review.getTaggedIssuesJson() != null) {
                tags = objectMapper.readValue(review.getTaggedIssuesJson(), new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {}

        return AiReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUser() != null ? review.getUser().getId() : null)
                .userEmail(review.getUser() != null ? review.getUser().getEmail() : null)
                .userFullName(review.getUser() != null ? review.getUser().getFullName() : null)
                .sessionId(review.getSessionId())
                .scenarioId(review.getScenarioId())
                .scenarioTitle(review.getScenarioTitle())
                .rating(review.getRating())
                .userFeedback(review.getUserFeedback())
                .taggedIssues(tags)
                .adminNote(review.getAdminNote())
                .reviewedByAdmin(review.getReviewedByAdmin())
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
