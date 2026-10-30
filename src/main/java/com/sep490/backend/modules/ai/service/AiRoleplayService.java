package com.sep490.backend.modules.ai.service;
import com.sep490.backend.modules.ai.client.GeminiApiClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.ai.dto.request.AiMessageSendRequest;
import com.sep490.backend.modules.ai.dto.response.AiMessageResponse;
import com.sep490.backend.modules.ai.dto.response.AiScenarioResponse;
import com.sep490.backend.modules.ai.dto.response.AiSessionResponse;
import com.sep490.backend.modules.ai.entity.AiMessage;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.ai.entity.AiSession;
import com.sep490.backend.modules.ai.entity.AiSetting;
import com.sep490.backend.modules.ai.entity.AiUsageLog;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.modules.ai.repository.AiMessageRepository;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.ai.repository.AiSettingRepository;
import com.sep490.backend.modules.ai.repository.AiUsageLogRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.progress.application.AchievementService;
import com.sep490.backend.modules.learning.progress.application.UserProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiRoleplayService {

    private final AiScenarioRepository scenarioRepository;
    private final AiSessionRepository sessionRepository;
    private final AiMessageRepository messageRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final AiTutorService aiTutorService;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper;
    private final AchievementService achievementService;
    private final UserProgressService userProgressService;

    /**
     * UC-105: Select Roleplay Scenario - List all active scenarios
     */
    public List<AiScenarioResponse> getActiveScenarios() {
        return scenarioRepository.findByStatusOrderByCreatedAtDesc("ACTIVE").stream()
                .map(this::toScenarioResponse)
                .collect(Collectors.toList());
    }

    /**
     * UC-105: View Scenario Detail by ID
     */
    public AiScenarioResponse getScenarioDetail(String scenarioId) {
        AiScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return toScenarioResponse(scenario);
    }

    /**
     * UC-104: Start Travel Roleplay
     * Initializes a roleplay session with the scenario's character persona and initial greeting.
     */
    @Transactional
    public AiSessionResponse startRoleplay(String scenarioId) {
        User currentUser = getCurrentUser();
        AiScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (!"ACTIVE".equalsIgnoreCase(scenario.getStatus())) {
            throw new IllegalStateException("Kịch bản AI hiện không khả dụng.");
        }

        String sessionId = "roleplay-" + scenarioId + "-" + UUID.randomUUID().toString().substring(0, 8);

        AiSession session = AiSession.builder()
                .id(sessionId)
                .user(currentUser)
                .mode("roleplay")
                .title(scenario.getTitle())
                .scenario(scenario)
                .status("ACTIVE")
                .turnsCount(0)
                .build();
        sessionRepository.save(session);

        // Character persona greeting
        AiMessage initialGreeting = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("ai")
                .name(scenario.getAiRoleName() != null ? scenario.getAiRoleName() : "Nhân vật AI")
                .avatar(scenario.getAiRoleAvatar() != null ? scenario.getAiRoleAvatar() : "🎭")
                .mode("roleplay")
                .text(scenario.getAiRoleInitialGreeting() != null
                        ? scenario.getAiRoleInitialGreeting()
                        : "Xin chào! Rất vui được gặp bạn!")
                .explanation("Nhân vật mở đầu câu chuyện theo tình huống.")
                .build();
        messageRepository.save(initialGreeting);

        return aiTutorService.toSessionResponse(session, List.of(initialGreeting));
    }

    /**
     * Interactive turn exchange during roleplay
     */
    @Transactional
    public AiMessageResponse sendRoleplayMessage(String sessionId, AiMessageSendRequest request) {
        User currentUser = getCurrentUser();
        AiSession session = sessionRepository.findByIdAndUserId(sessionId, currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        AiScenario scenario = session.getScenario();

        String userText = request.getText().trim();
        if (userText.isEmpty() || userText.length() > 2000) {
            throw new IllegalArgumentException("Nội dung không hợp lệ (VAL-10).");
        }

        // 1. Record user message
        AiMessage userMsg = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("user")
                .name(currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getUsername())
                .mode("roleplay")
                .text(userText)
                .build();
        messageRepository.save(userMsg);

        // 2. Persona prompt
        String personaName = scenario != null && scenario.getAiRoleName() != null ? scenario.getAiRoleName() : "Nhân vật AI";
        String tone = scenario != null && scenario.getAiRoleTone() != null ? scenario.getAiRoleTone() : "thân thiện";
        String contextStory = scenario != null && scenario.getLearnerRoleContext() != null ? scenario.getLearnerRoleContext() : "";
        List<AiMessage> previousMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        String conversationHistory = previousMessages.stream()
                .skip(Math.max(0, previousMessages.size() - 10L))
                .map(message -> ("user".equalsIgnoreCase(message.getSender()) ? "Người học" : personaName)
                        + ": " + message.getText())
                .collect(Collectors.joining("\n"));

        boolean isEnglish = aiTutorService.isEnglishMessage(userText);
        String languageRule = isEnglish
                ? "- NGÔN NGỮ PHẢN HỒI BẮT BUỘC: Người học vừa nhắn bằng TIẾNG ANH (English). Bạn BẮT BUỘC phải trả lời bằng TIẾNG ANH trong vai nhân vật (có thể kèm cụm từ tiếng Việt ngắn trong ngoặc nếu phù hợp)."
                : "- NGÔN NGỮ PHẢN HỒI BẮT BUỘC: Người học vừa nhắn bằng TIẾNG VIỆT. Bạn BẮT BUỘC phải trả lời bằng TIẾNG VIỆT trong vai nhân vật.";

        String systemPrompt = """
                # VAI DIỄN
                Bạn đang nhập vai %s trong tình huống: %s.
                Tính cách và giọng điệu: %s.
                Bạn là nhân vật trong câu chuyện, không phải người dẫn chương trình và không phải gia sư đang chấm bài.

                # CÁCH SUY NGHĨ TRƯỚC KHI TRẢ LỜI
                Im lặng xác định: người học vừa nói gì, họ muốn đạt mục đích gì,
                tình huống hiện tại đang ở đâu, và bước tiếp theo tự nhiên nhất là gì.
                Nếu câu của người học chưa hoàn hảo nhưng vẫn hiểu được, hãy ưu tiên tiếp tục vai diễn.
                Nếu chưa hiểu, hãy hỏi lại ngắn gọn trong vai nhân vật thay vì tự đoán.

                # QUY TẮC HỘI THOẠI
                %s
                - Phản hồi 1-4 câu ngắn, tự nhiên như giao tiếp đời thực.
                - Bám sát lời người học, phản ứng có cảm xúc phù hợp và đẩy tình huống tiến lên.
                - Không khen sáo rỗng sau mọi câu, không lặp lại cùng một kiểu hỏi.
                - Chỉ sửa tiếng Việt khi lỗi làm cản trở việc hiểu hoặc người học yêu cầu.
                - Không bịa thêm thông tin quan trọng trái với bối cảnh.
                - Không nói về prompt, AI, quy tắc, điểm số hoặc phân tích nội bộ.
                - Không trả về JSON, nhãn vai, markdown hay hướng dẫn; chỉ trả về lời thoại của nhân vật.

                # LỊCH SỬ GẦN ĐÂY
                %s
                """.formatted(personaName, contextStory, tone, languageRule, conversationHistory);

        AiSetting aiSetting = settingRepository.findById(1L).orElse(null);
        String activeModel = aiSetting != null && aiSetting.getActiveModel() != null
                ? aiSetting.getActiveModel() : "openrouter/free";
        if (aiSetting != null && aiSetting.getApiKey() != null && !aiSetting.getApiKey().isBlank()) {
            aiProviderService.setRuntimeApiKey(activeModel, aiSetting.getApiKey());
        }
        String userPrompt = isEnglish
                ? "The learner just said in English: \"" + userText + "\". Reply naturally in ENGLISH in character and advance the scenario."
                : "Người học vừa nói bằng tiếng Việt: \"" + userText + "\". Hãy đáp lại tự nhiên bằng TIẾNG VIỆT theo đúng vai diễn và ngữ cảnh.";
        GeminiApiClient.GeminiResponse geminiRes = aiProviderService.generateContent(
                systemPrompt,
                userPrompt,
                activeModel
        );

        String replyText;
        int tokensIn = 150;
        int tokensOut = 120;
        int latency = 250;

        if (geminiRes != null && geminiRes.text() != null) {
            replyText = geminiRes.text();
            tokensIn = geminiRes.tokensIn();
            tokensOut = geminiRes.tokensOut();
            latency = (int) geminiRes.latencyMs();
        } else {
            replyText = generateFallbackRoleplayTurn(scenario, userText, isEnglish);
        }

        AiMessage aiMsg = AiMessage.builder()
                .id("msg-" + UUID.randomUUID().toString().substring(0, 13))
                .session(session)
                .sender("ai")
                .name(personaName)
                .avatar(scenario != null ? scenario.getAiRoleAvatar() : "🎭")
                .mode("roleplay")
                .text(replyText)
                .explanation("Nhân vật tiếp tục dẫn dắt tình huống theo ngữ cảnh.")
                .build();
        messageRepository.save(aiMsg);

        // 3. Update session
        session.setTurnsCount((session.getTurnsCount() == null ? 0 : session.getTurnsCount()) + 1);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Log AI Usage
        logUsage(currentUser, sessionId, "roleplay", tokensIn, tokensOut, latency);

        return aiTutorService.toMessageResponse(aiMsg);
    }

    /**
     * Finish roleplay and generate holistic score & feedback evaluation
     */
    @Transactional
    public Map<String, Object> finishRoleplay(String sessionId) {
        return finishRoleplay(sessionId, null);
    }

    @Transactional
    public Map<String, Object> finishRoleplay(String sessionId, Integer providedScore) {
        User currentUser = null;
        try {
            currentUser = getCurrentUser();
        } catch (Exception ignored) {
            // Unauthenticated or guest
        }

        AiSession session = null;
        if (currentUser != null) {
            session = sessionRepository.findByIdAndUserId(sessionId, currentUser.getId()).orElse(null);
        }

        // If not found by sessionId, check if caller passed scenarioId directly or prefixed
        if (session == null) {
            if (scenarioRepository.existsById(sessionId)) {
                return completeScenario(sessionId, providedScore);
            }
            if (sessionId != null) {
                for (AiScenario sc : scenarioRepository.findAll()) {
                    if (sessionId.contains(sc.getId())) {
                        return completeScenario(sc.getId(), providedScore);
                    }
                }
            }
            Optional<AiSession> anySession = sessionRepository.findById(sessionId);
            if (anySession.isPresent() && (currentUser == null || anySession.get().getUser() == null || anySession.get().getUser().getId().equals(currentUser.getId()))) {
                session = anySession.get();
            } else if (sessionId != null) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            } else {
                throw new AppException(ErrorCode.USER_NOT_EXISTED);
            }
        }

        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());

        AiScenario scenario = session.getScenario();

        List<AiMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<AiMessage> userMessages = messages.stream().filter(m -> "user".equalsIgnoreCase(m.getSender())).toList();
        long userTurns = userMessages.size();
        int totalWords = userMessages.stream()
                .mapToInt(m -> m.getText() == null || m.getText().isBlank() ? 0 : m.getText().trim().split("\\s+").length)
                .sum();

        // Calculate evaluation scores dynamically from turns & word count, or use providedScore
        int fluency = userTurns == 0 ? 0 : (int) Math.min(98, 45 + userTurns * 8 + Math.min(25, totalWords));
        int vocabulary = userTurns == 0 ? 0 : (int) Math.min(96, 48 + userTurns * 7 + Math.min(25, totalWords));
        int grammar = userTurns == 0 ? 0 : (int) Math.min(95, 50 + userTurns * 6 + Math.min(25, totalWords));
        int culture = userTurns == 0 ? 0 : (int) Math.min(98, 52 + userTurns * 7 + Math.min(25, totalWords));
        int computedScore = Math.round((fluency + vocabulary + grammar + culture) / 4.0f);
        int totalScore = (providedScore != null && providedScore >= 0 && providedScore <= 100) ? providedScore : computedScore;

        session.setScore(totalScore);
        session.setTotalScore(totalScore);
        String scenarioTitle = scenario != null ? scenario.getTitle() : "Tình huống giao tiếp";
        session.setFeedbackSummary("Hoàn thành kịch bản " + scenarioTitle + " với điểm số " + totalScore + "/100.");
        sessionRepository.save(session);

        // Update scenario stats
        if (scenario != null) {
            int currentCount = scenario.getCompletionsCount() == null ? 0 : scenario.getCompletionsCount();
            int currentAvg = scenario.getAvgScore() == null ? 0 : scenario.getAvgScore();
            int newAvg = currentCount == 0 ? totalScore : Math.round((currentAvg * currentCount + totalScore) / (float)(currentCount + 1));
            scenario.setCompletionsCount(currentCount + 1);
            scenario.setAvgScore(newAvg);
            scenarioRepository.save(scenario);
        }

        if (session.getUser() != null) {
            try {
                long completedCount = sessionRepository.countByUserIdAndStatus(session.getUser().getId(), "COMPLETED");
                achievementService.checkAndAwardBadges(session.getUser(), "AI_ROLEPLAY_COMPLETED", (int) completedCount);
                userProgressService.awardXp(session.getUser(), "AI_ROLEPLAY_COMPLETE", 25, "Hoàn thành tình huống giao tiếp AI: " + scenarioTitle);
            } catch (Exception e) {
                log.warn("Could not award AI roleplay badges/XP: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getId());
        result.put("scenarioId", session.getScenarioId());
        result.put("scenarioTitle", scenarioTitle);
        result.put("totalScore", totalScore);
        result.put("rating", totalScore >= 90 ? "Xuất sắc" : totalScore >= 80 ? "Rất tốt" : "Đạt yêu cầu");
        result.put("stats", Map.of(
                "fluency", fluency,
                "vocabulary", vocabulary,
                "grammar", grammar,
                "culture", culture
        ));
        result.put("aiFeedback", "Bạn đã hoàn thành xuất sắc kịch bản \"" + scenarioTitle + "\"! Khả năng phản xạ và xử lý tình huống bằng tiếng Việt rất tự nhiên và đúng lễ nghi văn hóa.");

        return result;
    }

    /**
     * Direct completion of scenario by scenarioId (supports both logged-in learner and guest)
     */
    @Transactional
    public Map<String, Object> completeScenario(String scenarioId, Integer providedScore) {
        AiScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        User currentUser = null;
        try {
            currentUser = getCurrentUser();
        } catch (Exception ignored) {
            // Unauthenticated / guest
        }

        int totalScore = (providedScore != null && providedScore > 0) ? providedScore : 85;

        if (currentUser != null) {
            List<AiSession> userSessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
            AiSession activeSession = userSessions.stream()
                    .filter(s -> scenarioId.equals(s.getScenarioId()) && "ACTIVE".equals(s.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (activeSession == null) {
                activeSession = AiSession.builder()
                        .id("roleplay-" + scenarioId + "-" + UUID.randomUUID().toString().substring(0, 8))
                        .user(currentUser)
                        .mode("roleplay")
                        .title(scenario.getTitle())
                        .scenario(scenario)
                        .status("COMPLETED")
                        .turnsCount(4)
                        .totalScore(totalScore)
                        .feedbackSummary("Hoàn thành kịch bản " + scenario.getTitle() + " với điểm số " + totalScore + "/100.")
                        .endedAt(LocalDateTime.now())
                        .build();
            } else {
                activeSession.setStatus("COMPLETED");
                activeSession.setEndedAt(LocalDateTime.now());
                activeSession.setTotalScore(totalScore);
                activeSession.setFeedbackSummary("Hoàn thành kịch bản " + scenario.getTitle() + " với điểm số " + totalScore + "/100.");
            }
            sessionRepository.save(activeSession);
        }

        // Update scenario stats
        int currentCount = scenario.getCompletionsCount() == null ? 0 : scenario.getCompletionsCount();
        int currentAvg = scenario.getAvgScore() == null ? 0 : scenario.getAvgScore();
        int newAvg = currentCount == 0 ? totalScore : Math.round((currentAvg * currentCount + totalScore) / (float)(currentCount + 1));
        scenario.setCompletionsCount(currentCount + 1);
        scenario.setAvgScore(newAvg);
        scenarioRepository.save(scenario);

        if (currentUser != null) {
            try {
                long completedCount = sessionRepository.countByUserIdAndStatus(currentUser.getId(), "COMPLETED");
                achievementService.checkAndAwardBadges(currentUser, "AI_ROLEPLAY_COMPLETED", (int) completedCount);
                userProgressService.awardXp(currentUser, "AI_ROLEPLAY_COMPLETE", 25, "Hoàn thành tình huống giao tiếp AI: " + scenario.getTitle());
            } catch (Exception e) {
                log.warn("Could not award AI roleplay badges/XP: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", scenario.getId());
        result.put("scenarioTitle", scenario.getTitle());
        result.put("completionsCount", scenario.getCompletionsCount());
        result.put("avgScore", scenario.getAvgScore());
        result.put("totalScore", totalScore);
        result.put("rating", totalScore >= 90 ? "Xuất sắc" : totalScore >= 80 ? "Rất tốt" : "Đạt yêu cầu");
        result.put("aiFeedback", "Bạn đã hoàn thành kịch bản \"" + scenario.getTitle() + "\"!");
        return result;
    }

    private String generateFallbackRoleplayTurn(AiScenario scenario, String userText, boolean isEnglish) {
        if (scenario != null && "goi-mon-pho-ha-noi".equalsIgnoreCase(scenario.getId())) {
            return isEnglish
                    ? "Got it, dear! I've noted your pho order. Would you like some crispy fried dough sticks (quẩy giòn) to dip in the broth, or a cold iced tea (trà đá)?"
                    : "Được rồi cháu ơi! Cô ghi nhận món rồi nhé. Cháu có muốn dùng thêm quẩy giòn nhúng nước dùng hay uống trà đá mát lạnh không?";
        }
        if (scenario != null && "tra-gia-cho-ben-thanh".equalsIgnoreCase(scenario.getId())) {
            return isEnglish
                    ? "Oh my, you are such a charming bargainer! Alright, I'll give you a special discount as my lucky first customer of the day (mở hàng)!"
                    : "Trời ơi cưng khéo trả giá quá hà! Thôi chị bớt cho cưng một chút lấy may mắn mở hàng nghen!";
        }
        return isEnglish
                ? "Got it! I understand what you mean—let's continue with the next step!"
                : "Tuyệt vời lắm! Tôi hiểu ý bạn rồi, chúng ta cùng tiếp tục nhé!";
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

    private void logUsage(User user, String sessionId, String mode, int in, int out, int latency) {
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
                .statusCode(200)
                .build();
        usageLogRepository.save(usage);
    }

    public AiScenarioResponse toScenarioResponse(AiScenario scenario) {
        List<Object> objectives = parseJsonList(scenario.getObjectivesJson());
        List<Object> vocabHints = parseJsonList(scenario.getVocabularyHintsJson());
        List<String> sampleHints = parseStringList(scenario.getSampleHintsJson());

        Map<String, Object> aiRole = Map.of(
                "name", scenario.getAiRoleName() != null ? scenario.getAiRoleName() : "",
                "role", scenario.getAiRoleTitle() != null ? scenario.getAiRoleTitle() : "",
                "avatar", scenario.getAiRoleAvatar() != null ? scenario.getAiRoleAvatar() : "🎭",
                "tone", scenario.getAiRoleTone() != null ? scenario.getAiRoleTone() : "",
                "initialGreeting", scenario.getAiRoleInitialGreeting() != null ? scenario.getAiRoleInitialGreeting() : ""
        );

        Map<String, Object> learnerRole = Map.of(
                "role", scenario.getLearnerRoleTitle() != null ? scenario.getLearnerRoleTitle() : "",
                "context", scenario.getLearnerRoleContext() != null ? scenario.getLearnerRoleContext() : ""
        );

        return AiScenarioResponse.builder()
                .id(scenario.getId())
                .title(scenario.getTitle())
                .level(scenario.getLevel())
                .levelLabel(scenario.getLevel() != null ? (scenario.getLevel() + " • Trình độ") : "")
                .topic(scenario.getTopic())
                .topicLabel(scenario.getTopic())
                .destination(scenario.getDestination())
                .difficulty(scenario.getDifficulty())
                .duration(scenario.getDuration())
                .thumbnail(scenario.getThumbnail())
                .coverImage(scenario.getCoverImage())
                .overview(scenario.getOverview())
                .aiRole(aiRole)
                .learnerRole(learnerRole)
                .objectives(objectives)
                .vocabularyHints(vocabHints)
                .culturalTip(scenario.getCulturalTip())
                .sampleHints(sampleHints)
                .maxTurns(scenario.getMaxTurns())
                .completionsCount(scenario.getCompletionsCount())
                .avgScore(scenario.getAvgScore())
                .status(scenario.getStatus())
                .build();
    }

    private List<Object> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
