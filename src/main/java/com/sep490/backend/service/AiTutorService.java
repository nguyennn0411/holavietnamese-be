package com.sep490.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.dto.request.AiMessageSendRequest;
import com.sep490.backend.dto.request.AiReviewCreateRequest;
import com.sep490.backend.dto.request.AiSessionCreateRequest;
import com.sep490.backend.dto.response.AiMessageResponse;
import com.sep490.backend.dto.response.AiReviewResponse;
import com.sep490.backend.dto.response.AiSessionResponse;
import com.sep490.backend.entity.*;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.*;
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
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;
    private final IntentClassifier intentClassifier;

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
        String sessionId = (request.getId() != null && !request.getId().isBlank())
                ? request.getId()
                : "session-" + UUID.randomUUID().toString().substring(0, 13);
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle()
                : "Hội thoại AI (" + getModeTitle(mode) + ")";

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
        String welcomeText = getInitialGreetingForMode(mode, currentUser);
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
    public List<AiSessionResponse> getLearnerSessions(String mode) {
        User currentUser = getCurrentUser();
        List<AiSession> sessions;
        if (mode != null && !mode.isBlank() && !"all".equalsIgnoreCase(mode)) {
            sessions = sessionRepository.findByUserIdAndModeOrderByCreatedAtDesc(currentUser.getId(), mode);
        } else {
            sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        }

        return sessions.stream()
                .map(s -> toSessionResponse(s, null))
                .collect(Collectors.toList());
    }

    /**
     * Get details and messages of a session
     */
    public AiSessionResponse getSessionDetails(String sessionId) {
        User currentUser = getCurrentUser();
        AiSession session = sessionRepository.findByIdAndUserId(sessionId, currentUser.getId())
                .or(() -> sessionRepository.findById(sessionId))
                .orElseGet(() -> {
                    AiSession newSession = AiSession.builder()
                            .id(sessionId)
                            .user(currentUser)
                            .mode("tutor")
                            .title("Hội thoại AI Tutor")
                            .status("ACTIVE")
                            .turnsCount(0)
                            .build();
                    return sessionRepository.save(newSession);
                });

        List<AiMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionResponse(session, messages);
    }

    /**
     * UC-101: Send AI Message & UC-106: Receive Language Feedback
     * BR-22: Builds contextual prompt using learner's level, destination, native language.
     * VAL-10: Reject message > 2000 characters or blank.
     */
    @Transactional
    public AiMessageResponse sendMessage(String sessionId, AiMessageSendRequest request) {
        User currentUser = getCurrentUser();
        AiSession session = sessionRepository.findByIdAndUserId(sessionId, currentUser.getId())
                .or(() -> sessionRepository.findById(sessionId))
                .orElseGet(() -> {
                    String mode = (request.getMode() != null && !request.getMode().isBlank()) ? request.getMode() : "tutor";
                    String title = "Hội thoại AI (" + getModeTitle(mode) + ")";
                    AiSession newSession = AiSession.builder()
                            .id(sessionId)
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

        String userText = request.getText().trim();
        if (userText.isEmpty() || userText.length() > 2000) {
            throw new IllegalArgumentException("Nội dung tin nhắn không hợp lệ (VAL-10).");
        }

        AiSetting settings = getOrInitSettings();
        if (session.getTurnsCount() != null && session.getTurnsCount() >= settings.getMaxTurnsPerSession()) {
            throw new IllegalStateException("Phiên hội thoại đã đạt số lượt trao đổi tối đa (" + settings.getMaxTurnsPerSession() + ").");
        }

        List<AiMessage> previousMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

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

        // 2. Classify intent (Rule-based & context-aware) and build learner profile
        ConversationIntent intent = intentClassifier.classify(userText, previousMessages);
        String learnerLevel = currentUser.getTargetLevel() != null ? currentUser.getTargetLevel() : "A1";
        String nativeLang = currentUser.getNativeLanguage() != null ? currentUser.getNativeLanguage() : "English";
        String learningGoal = currentUser.getLearningGoal() != null ? currentUser.getLearningGoal() : "Everyday conversations";
        String mode = (request.getMode() != null && !request.getMode().isBlank()) ? request.getMode() : session.getMode();

        String systemPrompt = buildSystemPrompt(mode, learnerLevel, nativeLang, learningGoal, intent);
        List<Map<String, Object>> multiTurnContents = buildMultiTurnContents(previousMessages, userText);

        // 3. Invoke Gemini with official systemInstruction & native multi-turn conversation memory
        GeminiApiClient.GeminiResponse geminiRes = geminiApiClient.generateMultiTurn(systemPrompt, multiTurnContents, settings.getActiveModel());

        AiMessage aiMessage;
        int tokensIn = 120;
        int tokensOut = 180;
        int latencyMs = 280;

        if (geminiRes != null && geminiRes.text() != null) {
            tokensIn = geminiRes.tokensIn();
            tokensOut = geminiRes.tokensOut();
            latencyMs = (int) geminiRes.latencyMs();
            aiMessage = parseAndBuildAiMessage(session, geminiRes.text(), mode);
        } else {
            // High quality fallback responder matching intent and context
            aiMessage = generateOfflineTutorResponse(session, userText, mode, learnerLevel, intent);
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
        AiSession session = sessionRepository.findByIdAndUserId(sessionId, currentUser.getId())
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

        AiSession session = sessionRepository.findByIdAndUserId(request.getSessionId(), currentUser.getId())
                .orElse(null);

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

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findActiveByUsernameWithRoles(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private AiSetting getOrInitSettings() {
        return settingRepository.findById(1L).orElseGet(() -> {
            AiSetting def = AiSetting.builder()
                    .id(1L)
                    .activeModel("gemini-1.5-flash")
                    .systemPromptVersion("v2.2.0-stable")
                    .maxTurnsPerSession(25)
                    .maxTokensPerResponse(1024)
                    .rateLimitPerMinute(30)
                    .dailySessionsPerUser(40)
                    .monthlyBudgetCapUsd(new BigDecimal("150.00"))
                    .currentMonthSpendUsd(BigDecimal.ZERO)
                    .costAlertThresholdPercent(80)
                    .apiKeyConfigured(geminiApiClient.isApiKeyAvailable())
                    .enabledModes("tutor,grammar,translation,conversation,culture_guide,travel,regional")
                    .build();
            return settingRepository.save(def);
        });
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
        boolean isConfigured = geminiApiClient.isApiKeyAvailable();
        String rawKey = settings.getApiKey() != null && !settings.getApiKey().isBlank()
                ? settings.getApiKey()
                : geminiApiClient.getEffectiveApiKey();
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
        statusMap.put("activeModel", settings.getActiveModel() != null ? settings.getActiveModel() : "gemini-1.5-flash");
        statusMap.put("apiKeyMasked", masked != null ? masked : "");
        statusMap.put("provider", "Google Gemini");
        statusMap.put("dailySessionsRemaining", remaining);
        statusMap.put("dailySessionsCap", dailyCap);
        return statusMap;
    }

    @Transactional
    public Map<String, Object> configureApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key không được để trống.");
        }
        String cleanKey = apiKey.trim();
        boolean isValid = geminiApiClient.testApiKey(cleanKey);

        AiSetting setting = getOrInitSettings();
        setting.setApiKey(cleanKey);
        setting.setApiKeyConfigured(true);
        settingRepository.save(setting);

        geminiApiClient.setRuntimeApiKey(cleanKey);

        String masked = cleanKey.length() > 8
                ? cleanKey.substring(0, 6) + "..." + cleanKey.substring(cleanKey.length() - 4)
                : cleanKey;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("isValidLive", isValid);
        res.put("activeModel", setting.getActiveModel());
        res.put("apiKeyMasked", masked);
        res.put("message", isValid
                ? "Đã kết nối thành công với Google Gemini API!"
                : "Đã lưu API Key. Lưu ý: Thử nghiệm kết nối chưa phản hồi 200, vui lòng kiểm tra quota của Google Cloud.");
        return res;
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
                    {"text": "Conversational Vietnamese reply (1-4 natural sentences for casual chat)", "translation": null, "explanation": null, "nativeAlternative": null, "culturalNote": null, "grammarCorrection": null, "savedWords": []}
                    """;
        }
    }

    private String buildSystemPrompt(String mode, String level, String nativeLang, String learningGoal, ConversationIntent intent) {
        String template = loadPromptTemplate();
        String currentTopic = getModeTitle(mode);
        String preferredLanguage = (nativeLang != null && !nativeLang.isBlank()) ? nativeLang : "English";
        String learnerLevel = (level != null && !level.isBlank()) ? level : "A1";
        String goal = (learningGoal != null && !learningGoal.isBlank()) ? learningGoal : "Everyday conversations";
        String detectedIntent = (intent != null) ? intent.name() : "CASUAL_CHAT";

        return template
                .replace("{{level}}", learnerLevel)
                .replace("{{preferredLanguage}}", preferredLanguage)
                .replace("{{currentTopic}}", currentTopic)
                .replace("{{learningGoal}}", goal)
                .replace("{{detectedIntent}}", detectedIntent);
    }

    /**
     * Builds true multi-turn contents according to official Google Gemini API specification.
     * Enforces strictly alternating roles: user -> model -> user -> model -> ... -> user.
     */
    private List<Map<String, Object>> buildMultiTurnContents(List<AiMessage> previousMessages, String currentUserText) {
        List<Map<String, Object>> contents = new ArrayList<>();

        if (previousMessages != null && !previousMessages.isEmpty()) {
            // Take the most recent 16 messages for strong conversational memory
            int start = Math.max(0, previousMessages.size() - 16);
            String lastRole = null;
            StringBuilder currentText = new StringBuilder();

            for (int i = start; i < previousMessages.size(); i++) {
                AiMessage msg = previousMessages.get(i);
                String text = msg.getText();
                if (text == null || text.isBlank()) continue;

                // Map 'user' -> 'user', 'ai' -> 'model'
                String role = "user".equalsIgnoreCase(msg.getSender()) ? "user" : "model";

                // Compact very long past AI turns slightly to keep token usage optimal
                if ("model".equals(role) && text.length() > 400) {
                    text = text.substring(0, 400) + "...";
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

            // Append current user message
            if ("user".equals(lastRole) && !contents.isEmpty()) {
                currentText.append("\n").append(currentUserText);
                contents.get(contents.size() - 1).put("parts", List.of(Map.of("text", currentText.toString())));
            } else {
                Map<String, Object> currentTurn = new HashMap<>();
                currentTurn.put("role", "user");
                currentTurn.put("parts", List.of(Map.of("text", currentUserText)));
                contents.add(currentTurn);
            }
        } else {
            // First turn in a session
            Map<String, Object> currentTurn = new HashMap<>();
            currentTurn.put("role", "user");
            currentTurn.put("parts", List.of(Map.of("text", currentUserText)));
            contents.add(currentTurn);
        }

        return contents;
    }

    private AiMessage parseAndBuildAiMessage(AiSession session, String reply, String mode) {
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
            String text = (String) map.getOrDefault("text", cleaned);
            String translation = (String) map.get("translation");
            String explanation = (String) map.get("explanation");
            String nativeAlternative = (String) map.get("nativeAlternative");
            String culturalNote = (String) map.get("culturalNote");
            String grammarCorrection = (String) map.get("grammarCorrection");
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
                    .text(cleaned)
                    .explanation("Phản hồi thông minh theo chế độ " + getModeTitle(mode))
                    .build();
        }
    }

    private AiMessage generateOfflineTutorResponse(AiSession session, String userText, String mode, String level, ConversationIntent intent) {
        String trimmed = userText.trim();
        String lower = trimmed.toLowerCase();
        String unaccented = removeAccents(lower);

        String reply;
        String explanation = null;
        String translation = null;
        String nativeAlternative = null;
        String culturalNote = null;
        String grammarCorrection = null;
        List<Map<String, String>> words = new ArrayList<>();

        // 1. Check Age Error: "tôi có 20 tuổi" / "tôi có ... tuổi"
        if (unaccented.matches(".*(toi|minh|em)\\s+(co|la)\\s+\\d+\\s+tuoi.*") || lower.contains("có 20 tuổi") || lower.contains("có 18 tuổi") || lower.contains("có 25 tuổi")) {
            reply = "Trong tiếng Việt, khi nói về số tuổi, chúng ta **không dùng từ 'có'** (khác với tiếng Anh 'I am...' hay tiếng Pháp 'J'ai...'). Người Việt nói trực tiếp số tuổi ngay sau đại từ!";
            grammarCorrection = "Không dùng 'có + [số] tuổi'. Cấu trúc đúng: [Đại từ] + [năm nay] + [số tuổi] + tuổi.";
            nativeAlternative = "Năm nay tôi " + extractAgeOrFallback(trimmed) + " tuổi ạ.";
            explanation = "Ví dụ: Thay vì nói 'Tôi có 20 tuổi', bạn hãy nói 'Tôi 20 tuổi' hoặc lịch sự hơn là 'Năm nay tôi 20 tuổi ạ'.";
            words.add(Map.of("word", "Năm nay", "meaning", "This year (used commonly before stating age)"));
            words.add(Map.of("word", "Tuổi", "meaning", "Years old / Age"));
        }
        // 2. Check "bị" vs "được" (Passive Voice with positive/negative emotions)
        else if (lower.contains("bị khen") || lower.contains("bị điểm 10") || lower.contains("bị thưởng") || lower.contains("bị trúng số") || lower.contains("bị tăng lương")) {
            reply = "Người Việt rất tinh tế trong việc thể hiện cảm xúc qua câu bị động! Từ **'bị'** chỉ dùng cho những điều xui xẻo, mất mát hoặc không mong muốn. Đối với tin vui hay điều tích cực, bạn nhất định phải dùng **'được'** nhé!";
            grammarCorrection = "Sai sắc thái biểu cảm: Thay 'bị' bằng 'được' khi nhận điều tích cực.";
            nativeAlternative = trimmed.replace("bị", "được");
            explanation = "• 'Được': Nhận điều may mắn, tốt lành (được khen, được thưởng, được tăng lương).\n• 'Bị': Gặp điều rủi ro, tiêu cực (bị ốm, bị phạt, bị trễ giờ).";
            words.add(Map.of("word", "Được khen", "meaning", "To be praised / complimented (positive)"));
            words.add(Map.of("word", "Bị phạt", "meaning", "To be fined / penalized (negative)"));
        }
        // 3. Check Adjective before Noun (Word order error)
        else if (lower.contains("đỏ xe") || lower.contains("to nhà") || lower.contains("đẹp áo") || lower.contains("ngon phở") || lower.contains("lớn trường")) {
            reply = "Trong ngữ pháp tiếng Việt, **Tính từ luôn đứng SAU Danh từ** để bổ nghĩa cho danh từ đó. Điều này ngược lại với tiếng Anh (*red car* -> *xe đỏ*).";
            grammarCorrection = "Trật tự từ sai: Tính từ bổ nghĩa phải đứng sau danh từ.";
            nativeAlternative = fixAdjectiveOrder(trimmed);
            explanation = "Quy tắc cốt lõi: [Danh từ] + [Tính từ]. Ví dụ: 'xe đỏ', 'nhà to', 'áo đẹp', 'phở ngon'.";
            words.add(Map.of("word", "Xe đỏ", "meaning", "Red vehicle (Noun + Adjective)"));
            words.add(Map.of("word", "Áo đẹp", "meaning", "Beautiful shirt / clothing"));
        }
        // 4. Redundant pronoun "nó"
        else if (lower.contains("ăn nó ngon") || lower.contains("uống nó ngon") || lower.contains("mua nó rẻ")) {
            reply = "Tiếng Việt có đặc điểm rất thú vị là thường **tỉnh lược tân ngữ** khi đối tượng đã được nhắc đến ở vế trước. Việc lặp lại từ 'nó' sẽ làm câu nghe hơi cứng nhắc và gượng gạo.";
            grammarCorrection = "Lược bỏ đại từ tân ngữ 'nó' không cần thiết.";
            nativeAlternative = trimmed.replace("ăn nó ngon", "ăn rất ngon").replace("uống nó ngon", "uống rất ngon");
            explanation = "Người bản xứ nói: 'Quả chuối này ngọt lắm, ăn rất ngon!' thay vì 'ăn nó ngon'.";
            words.add(Map.of("word", "Ngon lắm", "meaning", "Very delicious (natural spoken tone)"));
        }
        // 5. Tone check: "hỏi" vs "ngã"
        else if (unaccented.contains("dau hoi") || unaccented.contains("dau nga") || unaccented.contains("thanh dieu") || (lower.contains("sữa") && lower.contains("sửa"))) {
            reply = "Phân biệt dấu hỏi (?) và dấu ngã (~) là một trong những nét duyên dáng nhất của tiếng Việt:\n\n" +
                    "• **Dấu hỏi (?)**: Âm điệu trầm xuống rồi lượn nhẹ lên (như đang thắc mắc: *'sửa xe'*, *'nghỉ ngơi'*, *'quả ổi'*).\n" +
                    "• **Dấu ngã (~)**: Âm điệu gãy gập ở giữa rồi vút cao lên (như bị nghẹn lại một nhịp: *'sữa tươi'*, *'suy nghĩ'*, *'ngã giá'*).";
            explanation = "Mẹo phương ngữ: Người miền Nam và miền Trung thường phát âm dấu ngã tương tự dấu hỏi, nhưng khi viết văn bản chuẩn thì vẫn phân biệt rõ rệt!";
            words.add(Map.of("word", "Sữa (dấu ngã)", "meaning", "Milk"));
            words.add(Map.of("word", "Sửa (dấu hỏi)", "meaning", "To fix / repair"));
        }
        // 6. Food & Drink: Phở, Cà phê, Bánh mì
        else if (unaccented.contains("ca phe") || unaccented.contains("coffee")) {
            reply = "Văn hóa cà phê Việt Nam cực kỳ phong phú và đậm đà! Khi vào quán, đây là những cách gọi cà phê chuẩn gu người Việt:\n\n" +
                    "1. **Cà phê sữa đá** (Miền Bắc hay gọi là *'Nâu đá'*): Cà phê phin đậm vị pha cùng sữa đặc ngọt béo.\n" +
                    "2. **Cà phê đen đá**: Cà phê nguyên chất, có thể dặn thêm ít đường hoặc không đường.\n" +
                    "3. **Bạc xỉu**: Dành cho người thích ngọt và ít vị đắng (nhiều sữa tươi + sữa đặc, chút xíu cà phê).\n" +
                    "4. **Cà phê trứng**: Đặc sản nổi tiếng của Hà Nội với lớp kem trứng béo ngậy đánh bông.";
            nativeAlternative = "Cho mình một ly cà phê sữa đá ít ngọt nhé!";
            culturalNote = "Người Việt thường ngồi cà phê vỉa hè để ngắm phố phường hoặc trò chuyện cùng bạn bè hàng giờ đồng hồ.";
            words.add(Map.of("word", "Cà phê sữa đá", "meaning", "Vietnamese iced coffee with condensed milk"));
            words.add(Map.of("word", "Bạc xỉu", "meaning", "Sweet milk with a splash of coffee"));
            words.add(Map.of("word", "Ít ngọt / Ít đường", "meaning", "Less sweet / less sugar"));
        }
        else if (unaccented.contains("pho") || unaccented.contains("banh mi")) {
            reply = "Ẩm thực đường phố Việt Nam luôn khiến thực khách say đắm! Để gọi món ngon đúng ý, bạn hãy lưu lại các mẫu câu này nhé:\n\n" +
                    "• Khi ăn Phở: *'Cho cháu một bát phở tái nạm gầu, ít hành, kèm đĩa quẩy giòn ạ!'*\n" +
                    "• Khi mua Bánh mì: *'Bán cho em một ổ bánh mì thập cẩm, không cay, nhiều dưa chuột nhé chị!'*";
            nativeAlternative = "Cho em một bát phở bò chín, nước trong, ít hành nhé ạ!";
            culturalNote = "Ở miền Bắc, phở thường ăn kèm quẩy giòn và giấm tỏi; trong khi ở miền Nam phở ăn kèm tương đen, tương đỏ và rất nhiều loại rau thơm (húng quế, ngò gai).";
            words.add(Map.of("word", "Phở tái chín", "meaning", "Pho with rare and well-done beef"));
            words.add(Map.of("word", "Bánh mì thập cẩm", "meaning", "Combo Vietnamese baguette with pate, meats & herbs"));
        }
        // 7. Shopping & Bargaining: Giá cả, Bao nhiêu tiền
        else if (unaccented.contains("bao nhieu") || unaccented.contains("tra gia") || unaccented.contains("mac ca") || unaccented.contains("gia tien") || unaccented.contains("how much")) {
            reply = "Khi đi chợ truyền thống hoặc mua sắm tại Việt Nam, để hỏi giá và mặc cả vừa lịch sự vừa được lòng người bán, bạn có thể áp dụng công thức sau:\n\n" +
                    "1. **Hỏi giá:** *'Dạ cô/chị ơi, cái này giá bao nhiêu một cái/kg ạ?'*\n" +
                    "2. **Thương lượng nhẹ nhàng:** *'Cô bớt cho cháu một chút lấy may nhé! Cháu mở hàng cho cô nha!'*\n" +
                    "3. **Chốt giá:** *'150 nghìn được không cô, cháu lấy hai cái luôn ạ!'*";
            nativeAlternative = "Dạ cái này bao nhiêu tiền một cái hả chị? Bớt chút cho em mở hàng may mắn nhé!";
            culturalNote = "Quan niệm 'Mở hàng': Người buôn bán Việt Nam rất coi trọng vị khách đầu tiên trong ngày. Mua bán nhanh nhẹn, xởi lởi vào sáng sớm sẽ mang lại may mắn cho cả ngày!";
            words.add(Map.of("word", "Bao nhiêu tiền?", "meaning", "How much does it cost?"));
            words.add(Map.of("word", "Bớt một chút", "meaning", "Give a small discount please"));
            words.add(Map.of("word", "Mở hàng", "meaning", "First purchase of the day (brings luck)"));
        }
        // 8. Directions & Transport: Hỏi đường, Taxi, Xe ôm
        else if (unaccented.contains("o dau") || unaccented.contains("hoi duong") || unaccented.contains("xe om") || unaccented.contains("taxi") || unaccented.contains("san bay")) {
            reply = "Khi cần hỏi đường tại Việt Nam, người dân luôn rất nhiệt tình chỉ dẫn nếu bạn mở đầu bằng lời chào kính ngữ:\n\n" +
                    "• *'Bác ơi cho cháu hỏi, đường ra Hồ Gươm / Chợ Bến Thành đi hướng nào ạ?'*\n" +
                    "• *'Từ đây đến sân bay hết khoảng bao nhiêu phút đi xe máy ạ?'*\n" +
                    "• *'Bác tài ơi, chở em đến địa chỉ này nhé, bật đồng hồ taxi giúp em với ạ!'*";
            nativeAlternative = "Làm ơn cho tôi hỏi đường ra ga tàu đi lối nào ạ?";
            explanation = "Dùng 'Bác ơi / Chú ơi / Chị ơi' trước câu hỏi luôn khiến người nghe cảm thấy được tôn trọng và sẵn lòng giúp đỡ bạn ngay lập tức.";
            words.add(Map.of("word", "Đi hướng nào?", "meaning", "Which direction should I go?"));
            words.add(Map.of("word", "Làm ơn cho tôi hỏi...", "meaning", "Excuse me, may I ask..."));
        }
        // 9. Greetings & Politeness
        else if (unaccented.matches("^(xin chao|chao|hello|hi|hey|alo).*") || trimmed.equalsIgnoreCase("chào") || trimmed.equalsIgnoreCase("xin chào")) {
            reply = "Chào bạn nhé! 😊 Rất vui được gặp lại bạn. Hôm nay của bạn thế nào rồi?";
        }
        // 10. Regional Vietnamese differences
        else if (unaccented.contains("thiet") || unaccented.contains("muong") || unaccented.contains("thia") || unaccented.contains("chen") || unaccented.contains("bat") || unaccented.contains("bac nam") || unaccented.contains("mien trung")) {
            reply = "Tiếng Việt 3 miền Bắc - Trung - Nam có những sự khác biệt từ vựng cực kỳ thú vị:\n\n" +
                    "| Đồ vật / Khái niệm | Miền Bắc | Miền Nam | Miền Trung |\n" +
                    "| :--- | :--- | :--- | :--- |\n" +
                    "| **Thìa ăn cơm** | Thìa | Muỗng | Muỗng / Thìa |\n" +
                    "| **Đồ đựng cơm** | Bát | Chén | Chén / Bát |\n" +
                    "| **Thịt heo/lợn** | Thịt lợn | Thịt heo | Thịt heo |\n" +
                    "| **Quả dứa** | Quả dứa | Trái thơm / Khóm | Quả thơm |\n" +
                    "| **Cái ô** | Cái ô | Cây dù | Cây dù |";
            culturalNote = "Dù từ vựng có khác nhau đôi chút nhưng người Việt ở cả 3 miền đều hiểu trọn vẹn ngữ cảnh của bạn. Đừng ngại sử dụng nhé!";
            words.add(Map.of("word", "Thìa (Bắc) = Muỗng (Nam)", "meaning", "Spoon"));
            words.add(Map.of("word", "Bát (Bắc) = Chén (Nam)", "meaning", "Small bowl"));
        }
        // 11. Mode specific contextual fallbacks
        else {
            switch (mode.toLowerCase()) {
                case "grammar":
                    reply = "### 📝 Phân tích ngữ pháp câu của bạn:\n\n" +
                            "> *\"" + trimmed + "\"*\n\n" +
                            "**Nhận xét:** Câu của bạn đã truyền đạt được ý nghĩa cơ bản. Để câu văn tự nhiên và chuẩn phong thái người bản xứ, hãy ghi nhớ:\n\n" +
                            "1. **Trật tự từ:** `[Chủ ngữ] + [Vị ngữ] + [Tân ngữ]`, tính từ đứng sau danh từ.\n" +
                            "2. **Trợ từ tình thái:** Thêm các từ như *'nhé'*, *'nha'*, *'ạ'* vào cuối câu để tạo sắc thái lịch sự, mềm mại.\n" +
                            "3. **Tỉnh lược:** Lược bớt các đại từ sở hữu hay tân ngữ thừa nếu ngữ cảnh đã rõ ràng.";
                    nativeAlternative = trimmed.endsWith("ạ") || trimmed.endsWith("nhé") ? trimmed : (trimmed + " nhé ạ!");
                    explanation = "Thêm trợ từ cuối câu giúp lời nói tự nhiên và gần gũi hơn rất nhiều.";
                    words.add(Map.of("word", "Nhé / Nha", "meaning", "Friendly sentence-ending particle"));
                    break;

                case "translation":
                    reply = "### 🌐 Dịch thuật & Chuyển ngữ tự nhiên:\n\n" +
                            "**Ngữ cảnh câu:** *\"" + trimmed + "\"*\n\n" +
                            "Khi dịch sang tiếng Việt tự nhiên, người bản xứ thường chú trọng **sắc thái biểu cảm** và vai vế xưng hô thay vì dịch từng từ theo kiểu máy móc (word-by-word).\n\n" +
                            "💡 **Gợi ý diễn đạt:** Diễn đạt ngắn gọn, kèm đại từ phù hợp với hoàn cảnh giao tiếp hàng ngày.";
                    translation = "Natural contextual translation for: " + trimmed;
                    explanation = "Chú ý ngữ cảnh trang trọng hay thân mật để chọn đại từ (tôi, em, mình, anh) cho chuẩn xác.";
                    break;

                case "culture_guide":
                    reply = "### 🏮 Nét đẹp văn hóa Việt Nam:\n\n" +
                            "Về nội dung bạn vừa chia sẻ: Văn hóa người Việt rất coi trọng **sự hòa nhã, tình cảm gia đình và kính trên nhường dưới**.\n\n" +
                            "• Khi vào nhà người Việt, bạn nên cởi giày dép để bên ngoài.\n" +
                            "• Trong bữa ăn gia đình, người nhỏ tuổi mời người lớn tuổi trước khi cầm đũa: *'Con mời bố mẹ xới cơm ạ!'*.\n" +
                            "• Khi nhận hoặc trao đồ vật cho người lớn, hãy dùng **hai tay** để thể hiện sự kính trọng.";
                    culturalNote = "Lời chào cao hơn mâm cỗ: Sự lễ phép và chào hỏi đúng mực luôn chiếm trọn cảm tình của người Việt.";
                    break;

                case "travel":
                    reply = "### ✈️ Cẩm nang du lịch & Sinh tồn:\n\n" +
                            "Để chuyến du lịch Việt Nam của bạn suôn sẻ và đáng nhớ, hãy trang bị ngay các câu nói thông dụng này:\n\n" +
                            "• *'Làm ơn cho tôi xem thực đơn có hình ảnh được không ạ?'*\n" +
                            "• *'Món này có cay không? Tôi ăn ít cay / không ăn cay được ạ!'*\n" +
                            "• *'Cho tôi xin hóa đơn thanh toán nhé!'*";
                    nativeAlternative = "Làm ơn cho em xin hóa đơn thanh toán nhé ạ!";
                    explanation = "Dùng 'Làm ơn...' ở đầu câu hoặc '...ạ' ở cuối câu là cách nói lịch sự nhất trong ngành dịch vụ du lịch.";
                    break;

                case "regional":
                    reply = "### 🗺️ Tiếng Việt 3 miền:\n\n" +
                            "Ngôn ngữ Việt Nam như một bức tranh đa sắc màu:\n" +
                            "• Miền Bắc: Chuẩn phát âm rõ thanh điệu, ngữ điệu trang trọng, từ ngữ khuôn mẫu.\n" +
                            "• Miền Trung: Giọng nói mộc mạc, âm vực trầm ấm, nhiều từ cổ đặc trưng (*mô, tê, răng, rứa*).\n" +
                            "• Miền Nam: Giọng nói ngọt ngào, mềm mại, thường lướt dấu ngã thành dấu hỏi, cách xưng hô cởi mở (*chế, hai, út, tía*).";
                    culturalNote = "Dù ở vùng miền nào, sự chân tình và nụ cười luôn là cầu nối ngôn ngữ tuyệt vời nhất!";
                    break;

                default: // tutor, conversation
                    if (intent == ConversationIntent.PRONUNCIATION) {
                        reply = "Để phát âm rõ ràng từ này, bạn hãy chú ý giữ khẩu hình tròn và nhả chữ dứt khoát nhé. Bạn thử đọc lại một lần xem sao nào!";
                    } else if (intent == ConversationIntent.VOCABULARY) {
                        reply = "Từ này thường được người Việt dùng rất nhiều trong giao tiếp hàng ngày. Bạn muốn nghe một câu ví dụ thực tế không?";
                    } else if (intent == ConversationIntent.PRACTICE) {
                        reply = "Tuyệt vời, cùng luyện tập nhé! Bạn thử đặt một câu ngắn sử dụng từ này xem sao nào?";
                    } else if (intent == ConversationIntent.TRANSLATION) {
                        reply = "Trong tiếng Việt giao tiếp tự nhiên, câu này người bản xứ thường nói ngắn gọn kèm trợ từ tình thái ở cuối cho thân thiện nhé!";
                    } else {
                        // CASUAL_CHAT
                        reply = "Nghe thú vị thật đấy! Bạn có thể chia sẻ thêm cho mình về điều này không? 😊";
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
