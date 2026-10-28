package com.sep490.backend.modules.ai.service;
import com.sep490.backend.modules.ai.client.GeminiApiClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.ai.dto.request.AiMessageSendRequest;
import com.sep490.backend.modules.ai.dto.response.AiMessageResponse;
import com.sep490.backend.modules.ai.dto.response.AiSessionResponse;
import com.sep490.backend.modules.ai.entity.AiMessage;
import com.sep490.backend.modules.ai.entity.AiSession;
import com.sep490.backend.modules.ai.entity.AiSetting;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.modules.ai.repository.AiMessageRepository;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.ai.repository.AiSettingRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCourseCoachService {

    private final AiSessionRepository sessionRepository;
    private final AiMessageRepository messageRepository;
    private final AiSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    private static final int MAX_LESSON_CONTENT_CHARS = 2000;
    private static final int MAX_HISTORY_MESSAGES = 10;

    @Transactional
    public AiSessionResponse getOrCreateCoachSession(Long courseId, Long lessonId, String lessonTitle) {
        User user = getCurrentUser();
        String sessionId = "coach-" + courseId + "-" + lessonId + "-u" + user.getId();

        return sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .map(s -> toSessionResponse(s, messageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId())))
                .orElseGet(() -> {
                    AiSession session = AiSession.builder()
                            .id(sessionId)
                            .user(user)
                            .mode("course_coach")
                            .title("AI Coach – " + (lessonTitle != null && !lessonTitle.isBlank() ? lessonTitle : ("Bài học " + lessonId)))
                            .courseId(courseId)
                            .lessonId(lessonId)
                            .coachMode("ask")
                            .status("ACTIVE")
                            .turnsCount(0)
                            .build();
                    sessionRepository.save(session);
                    return toSessionResponse(session, List.of());
                });
    }

    @Transactional
    public AiMessageResponse sendCoachMessage(String sessionId, AiMessageSendRequest request) {
        User user = getCurrentUser();
        AiSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseGet(() -> {
                    Long cId = request.getCourseId();
                    Long lId = request.getLessonId();
                    if (cId != null && lId != null) {
                        String standardSessionId = "coach-" + cId + "-" + lId + "-u" + user.getId();
                        return sessionRepository.findByIdAndUserId(standardSessionId, user.getId())
                                .orElseGet(() -> {
                                    AiSession newSession = AiSession.builder()
                                            .id(standardSessionId)
                                            .user(user)
                                            .mode("course_coach")
                                            .title("AI Coach – " + (request.getLessonTitle() != null && !request.getLessonTitle().isBlank() ? request.getLessonTitle() : "Bài học " + lId))
                                            .courseId(cId)
                                            .lessonId(lId)
                                            .coachMode("ask")
                                            .status("ACTIVE")
                                            .turnsCount(0)
                                            .build();
                                    return sessionRepository.save(newSession);
                                });
                    }
                    AiSession fallbackSession = AiSession.builder()
                            .id(sessionId)
                            .user(user)
                            .mode("course_coach")
                            .title("AI Coach")
                            .coachMode("ask")
                            .status("ACTIVE")
                            .turnsCount(0)
                            .build();
                    return sessionRepository.save(fallbackSession);
                });

        String userText = sanitize(request.getText());
        String coachMode = request.getCoachMode() != null && !request.getCoachMode().isBlank()
                ? request.getCoachMode().trim()
                : (session.getCoachMode() != null ? session.getCoachMode() : "ask");
        session.setCoachMode(coachMode);

        String language = resolveResponseLanguage(userText, request.getLanguage(), user.getNativeLanguage());

        // 1. Save user message
        AiMessage userMessage = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("user")
                .name(user.getFullName() != null ? user.getFullName() : user.getUsername())
                .mode("course_coach")
                .text(userText)
                .build();
        messageRepository.save(userMessage);

        // 2. Fetch history
        List<AiMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        // 3. Build system instruction
        String systemInstruction = buildCoachSystemPrompt(request, coachMode, language, user);

        // 4. Build multi-turn contents (role alternating: user/model)
        List<Map<String, Object>> multiTurnContents = buildMultiTurnContents(history, userText, language);

        // 5. Call AI
        AiSetting settings = getOrInitSettings();
        String activeModel = settings.getActiveModel() != null ? settings.getActiveModel() : "openrouter/free";
        GeminiApiClient.GeminiResponse geminiRes = null;
        try {
            geminiRes = aiProviderService.generateMultiTurn(
                    systemInstruction, multiTurnContents, activeModel
            );
        } catch (Exception e) {
            log.warn("[AiCourseCoach] AI provider multi-turn call failed: {}", e.getMessage());
        }

        String aiText = null;
        if (geminiRes != null && geminiRes.text() != null && !geminiRes.text().isBlank()) {
            aiText = parseAiText(geminiRes.text());
        }

        if (aiText == null || aiText.isBlank()) {
            aiText = generateFallbackCoachAnswer(userText, request.getLessonTitle(), request.getLessonContentSnippet(), language);
        }

        // 6. Save AI message
        AiMessage aiMessage = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("ai")
                .name("AI Course Coach")
                .mode("course_coach")
                .text(aiText)
                .tokensIn(geminiRes != null ? geminiRes.tokensIn() : 0)
                .tokensOut(geminiRes != null ? geminiRes.tokensOut() : 0)
                .latencyMs(geminiRes != null ? (int) Math.min(geminiRes.latencyMs(), Integer.MAX_VALUE) : 0)
                .build();
        messageRepository.save(aiMessage);

        session.setTurnsCount((session.getTurnsCount() != null ? session.getTurnsCount() : 0) + 1);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return toMessageResponse(aiMessage);
    }

    private String generateFallbackCoachAnswer(String userText, String lessonTitle, String contentSnippet, String language) {
        String q = userText != null ? userText.toLowerCase(Locale.ROOT) : "";
        boolean isEn = "English".equalsIgnoreCase(language);

        if (q.contains("chữ cái") || q.contains("bảng chữ cái") || q.contains("alphabet") || q.contains("bao nhiêu")) {
            return isEn
                    ? "The modern Vietnamese alphabet consists of **29 letters**: 12 vowels (*a, ă, â, e, ê, i, o, ô, ơ, u, ư, y*) and 17 consonants. In particular, it features distinct diacritic letters: **ă, â, đ, ê, ô, ơ, ư**."
                    : "Bảng chữ cái tiếng Việt hiện đại bao gồm **29 chữ cái** chính thức: 12 nguyên âm (*a, ă, â, e, ê, i, o, ô, ơ, u, ư, y*) và 17 phụ âm. Trong đó có các chữ cái đặc trưng có dấu phụ như: **ă, â, đ, ê, ô, ơ, ư**.";
        }

        if (q.contains("thanh điệu") || q.contains("dấu") || q.contains("tones")) {
            return isEn
                    ? "Vietnamese has **6 tones**: Ngang (flat), Huyền (falling `\\`), Sắc (rising `/`), Hỏi (asking `?`), Ngã (tumbling `~`), and Nặng (dot below `.`)."
                    : "Tiếng Việt có **6 thanh điệu**: thanh ngang (không dấu), huyền (`\\`), sắc (`/`), hỏi (`?`), ngã (`~`), và nặng (`.`).";
        }

        String title = lessonTitle != null && !lessonTitle.isBlank() ? lessonTitle : "bài học này";
        return isEn
                ? "Regarding **" + title + "**: Focus on the pronunciation, vocabulary, and key sentence structures highlighted in your lesson. Would you like me to explain further or give more practice examples?"
                : "Về **" + title + "**: Bạn hãy tập trung vào phần phát âm chuẩn và các từ vựng cốt lõi trong bài học nhé. Bạn có muốn mình giải thích chi tiết hơn hay cho thêm ví dụ luyện tập không?";
    }

    private String buildCoachSystemPrompt(AiMessageSendRequest req, String coachMode, String language, User user) {
        String template = loadPromptTemplate();
        String lessonContent = req.getLessonContentSnippet() != null && !req.getLessonContentSnippet().isBlank()
                ? truncate(req.getLessonContentSnippet(), MAX_LESSON_CONTENT_CHARS)
                : "(Không có nội dung bài học được đính kèm)";

        String lessonTitle = req.getLessonTitle() != null && !req.getLessonTitle().isBlank()
                ? req.getLessonTitle()
                : "Bài học hiện tại";
        String level = user.getTargetLevel() != null ? user.getTargetLevel() : "A1";

        return template
                .replace("{{courseTitle}}", "Khóa học")
                .replace("{{courseLevel}}", level)
                .replace("{{lessonTitle}}", lessonTitle)
                .replace("{{level}}", level)
                .replace("{{coachMode}}", coachMode)
                .replace("{{preferredLanguage}}", language)
                .replace("{{lessonContent}}", lessonContent);
    }

    private List<Map<String, Object>> buildMultiTurnContents(List<AiMessage> history, String currentUserText, String preferredLanguage) {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            String lastRole = null;
            StringBuilder currentText = new StringBuilder();

            for (int i = start; i < history.size() - 1; i++) {
                AiMessage msg = history.get(i);
                String text = msg.getText();
                if (text == null || text.isBlank()) continue;

                String role = "user".equalsIgnoreCase(msg.getSender()) ? "user" : "model";
                if ("model".equals(role) && text.length() > 400) {
                    text = text.substring(0, 400) + "...";
                }

                if (role.equals(lastRole)) {
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

            while (!contents.isEmpty() && "model".equals(contents.get(0).get("role"))) {
                contents.remove(0);
            }
        }

        Map<String, Object> currentTurn = new HashMap<>();
        currentTurn.put("role", "user");
        currentTurn.put("parts", List.of(Map.of("text", currentUserText)));
        contents.add(currentTurn);

        return contents;
    }

    private String parseAiText(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        try {
            Map<?, ?> json = objectMapper.readValue(trimmed, Map.class);
            if (json.containsKey("text") && json.get("text") != null) {
                return String.valueOf(json.get("text")).trim();
            }
        } catch (Exception ignored) {
            // fallback if not valid JSON
        }
        return trimmed;
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)\\n*\\[(?:Language rule|Quy tắc ngôn ngữ):.*\\]\\s*$", "")
                .trim()
                .substring(0, Math.min(text.trim().length(), 2000));
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private String resolveResponseLanguage(String userText, String requestedLanguage, String userNativeLanguage) {
        if (userText != null && !userText.isBlank()) {
            boolean isEn = userText.toLowerCase(Locale.ROOT).matches(".*\\b(what|how|why|when|where|explain|translate|meaning|hello|help|lesson)\\b.*");
            return isEn ? "English" : "Vietnamese";
        }
        if (requestedLanguage != null && !requestedLanguage.isBlank()) {
            return requestedLanguage.toLowerCase(Locale.ROOT).startsWith("en") ? "English" : "Vietnamese";
        }
        return (userNativeLanguage != null && userNativeLanguage.toLowerCase(Locale.ROOT).startsWith("en")) ? "English" : "Vietnamese";
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/ai-course-coach.txt");
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load ai-course-coach.txt prompt template: {}", e.getMessage());
            return "You are an AI Course Coach assisting a learner with Vietnamese course lessons. Mode: {{coachMode}}. Lesson: {{lessonTitle}}. Content: {{lessonContent}}. Respond in {{preferredLanguage}}.";
        }
    }

    private AiSetting getOrInitSettings() {
        AiSetting settings = settingRepository.findById(1L).orElseGet(() ->
                settingRepository.save(AiSetting.builder()
                        .id(1L)
                        .activeModel("openrouter/free")
                        .systemPromptVersion("v2.2.0-stable")
                        .maxTurnsPerSession(25)
                        .maxTokensPerResponse(1024)
                        .rateLimitPerMinute(30)
                        .dailySessionsPerUser(40)
                        .monthlyBudgetCapUsd(new java.math.BigDecimal("150.00"))
                        .currentMonthSpendUsd(java.math.BigDecimal.ZERO)
                        .costAlertThresholdPercent(80)
                        .apiKeyConfigured(aiProviderService.isApiKeyAvailable("openrouter/free"))
                        .enabledModes("tutor,grammar,translation,conversation,culture_guide,travel,regional")
                        .build())
        );
        if (settings.getApiKey() != null && !settings.getApiKey().isBlank()) {
            aiProviderService.setRuntimeApiKey(settings.getActiveModel(), settings.getApiKey());
        }
        return settings;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (auth.getPrincipal() instanceof LearnerPrincipal lp && lp.id() != null) {
            return userRepository.findById(lp.id()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        }
        return userRepository.findActiveByUsernameWithRoles(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private AiSessionResponse toSessionResponse(AiSession s, List<AiMessage> messages) {
        return AiSessionResponse.builder()
                .id(s.getId())
                .mode(s.getMode())
                .title(s.getTitle())
                .status(s.getStatus())
                .messages(messages.stream().map(this::toMessageResponse).toList())
                .build();
    }

    private AiMessageResponse toMessageResponse(AiMessage m) {
        return AiMessageResponse.builder()
                .id(m.getId())
                .sender(m.getSender())
                .name(m.getName())
                .avatar(m.getAvatar())
                .text(m.getText())
                .translation(m.getTranslation())
                .explanation(m.getExplanation())
                .timestamp(m.getCreatedAt())
                .build();
    }
}
