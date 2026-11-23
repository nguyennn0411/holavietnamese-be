package com.sep490.backend.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.ai.client.GeminiApiClient;
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
import com.sep490.backend.modules.ai.entity.AiSession;
import com.sep490.backend.modules.ai.entity.AiSetting;
import com.sep490.backend.modules.ai.entity.AiUsageLog;
import com.sep490.backend.modules.ai.repository.AiMessageRepository;
import com.sep490.backend.modules.ai.repository.AiReviewRepository;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.ai.repository.AiSettingRepository;
import com.sep490.backend.modules.ai.repository.AiUsageLogRepository;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Isolated unit tests for AiTutorService following the f9fac16 pattern.
 * Mocks repositories and LLM external services to verify business logic,
 * quota enforcement, provider routing, offline fallback, and security boundaries.
 */
class AiTutorServiceTests {

    private AiSessionRepository sessionRepository;
    private AiMessageRepository messageRepository;
    private AiSettingRepository settingRepository;
    private AiScenarioRepository scenarioRepository;
    private AiReviewRepository reviewRepository;
    private AiUsageLogRepository usageLogRepository;
    private UserRepository userRepository;
    private AiProviderService aiProviderService;
    private ObjectMapper objectMapper;
    private IntentClassifier intentClassifier;

    private AiTutorService tutorService;
    private User testUser;
    private AiSetting testSettings;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(AiSessionRepository.class);
        messageRepository = mock(AiMessageRepository.class);
        settingRepository = mock(AiSettingRepository.class);
        scenarioRepository = mock(AiScenarioRepository.class);
        reviewRepository = mock(AiReviewRepository.class);
        usageLogRepository = mock(AiUsageLogRepository.class);
        userRepository = mock(UserRepository.class);
        aiProviderService = mock(AiProviderService.class);
        objectMapper = new ObjectMapper();
        intentClassifier = mock(IntentClassifier.class);

        tutorService = new AiTutorService(
                sessionRepository,
                messageRepository,
                settingRepository,
                scenarioRepository,
                reviewRepository,
                usageLogRepository,
                userRepository,
                aiProviderService,
                objectMapper,
                intentClassifier
        );

        testUser = buildUser(1L, "learner", "learner@example.com", "A1", "English");
        testSettings = buildSettings();

        // Setup mock authentication in SecurityContext
        var principal = new LearnerPrincipal(1L, "learner@example.com", "ROLE_LEARNER", true);
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(settingRepository.findById(1L)).thenReturn(Optional.of(testSettings));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSessionEnforcesDailyQuotaAndThrowsWhenLimitReached() {
        testSettings.setDailySessionsPerUser(3);
        when(sessionRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(3L);

        var request = AiSessionCreateRequest.builder().mode("tutor").title("Test").build();

        assertThatThrownBy(() -> tutorService.createSession(request))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED));

        verify(sessionRepository, never()).save(any(AiSession.class));
    }

    @Test
    void createSessionSavesActiveSessionAndInitialGreeting() {
        when(sessionRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(sessionRepository.findByIdAndUserId(anyString(), eq(1L))).thenReturn(Optional.empty());

        var request = AiSessionCreateRequest.builder().id("session-101").mode("grammar").title("Ngữ pháp").build();
        AiSessionResponse response = tutorService.createSession(request);

        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo("grammar");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<AiSession> sessionCaptor = ArgumentCaptor.forClass(AiSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(sessionCaptor.getValue().getTurnsCount()).isEqualTo(0);

        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSender()).isEqualTo("ai");
        assertThat(messageCaptor.getValue().getText()).isNotBlank();
        assertThat(messageCaptor.getValue().getExplanation()).contains("Ngữ pháp");
    }

    @Test
    void createSessionReturnsExistingSessionIfAlreadyPresent() {
        when(sessionRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(1L);

        var existingSession = buildSession("session-101", testUser, "grammar", "ACTIVE", 2);
        var existingMsg = buildMessage("msg-1", existingSession, "ai", "Xin chào");

        when(sessionRepository.findByIdAndUserId("session-101", 1L)).thenReturn(Optional.of(existingSession));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session-101")).thenReturn(List.of(existingMsg));

        var request = AiSessionCreateRequest.builder().id("session-101").mode("grammar").build();
        AiSessionResponse response = tutorService.createSession(request);

        assertThat(response.getId()).isEqualTo("session-101");
        assertThat(response.getMessages()).hasSize(1);
        verify(sessionRepository, never()).save(any(AiSession.class));
    }

    @Test
    void getLearnerSessionsFiltersByModeAndExcludesRoleplayByDefault() {
        var tutorSession = buildSession("s-1", testUser, "tutor", "ACTIVE", 1);
        var grammarSession = buildSession("s-2", testUser, "grammar", "ACTIVE", 1);
        var roleplaySession = buildSession("s-3", testUser, "roleplay", "ACTIVE", 1);

        when(sessionRepository.findByUserIdAndModeOrderByCreatedAtDesc(1L, "grammar"))
                .thenReturn(List.of(grammarSession));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(tutorSession, grammarSession, roleplaySession));

        // When mode is specified
        List<AiSessionResponse> grammarResults = tutorService.getLearnerSessions("grammar");
        assertThat(grammarResults).hasSize(1);
        assertThat(grammarResults.get(0).getMode()).isEqualTo("grammar");

        // When mode is null or 'all', roleplay must be filtered out
        List<AiSessionResponse> allResults = tutorService.getLearnerSessions(null);
        assertThat(allResults).extracting(AiSessionResponse::getMode)
                .containsExactly("tutor", "grammar");
    }

    @Test
    void updateSessionUpdatesTitleAndModeAndRejectsUnauthorizedUser() {
        var session = buildSession("s-10", testUser, "tutor", "ACTIVE", 0);
        when(sessionRepository.findByIdAndUserId("s-10", 1L)).thenReturn(Optional.of(session));

        AiSessionResponse updated = tutorService.updateSession("s-10", Map.of("title", "New Title", "mode", "travel"));
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getMode()).isEqualTo("travel");
        verify(sessionRepository).save(session);

        // Unauthorized when session belongs to another user
        when(sessionRepository.findByIdAndUserId("s-other", 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tutorService.updateSession("s-other", Map.of("title", "Hack")))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void deleteSessionRemovesOnlyUserOwnedSession() {
        var session = buildSession("s-del", testUser, "tutor", "ACTIVE", 0);
        when(sessionRepository.findByIdAndUserId("s-del", 1L)).thenReturn(Optional.of(session));

        tutorService.deleteSession("s-del");
        verify(sessionRepository).delete(session);

        // When not found, delete is safely ignored
        when(sessionRepository.findByIdAndUserId("s-none", 1L)).thenReturn(Optional.empty());
        tutorService.deleteSession("s-none");
        verify(sessionRepository, never()).delete(null);
    }

    @Test
    void sendMessageRejectsInactiveOrExceededTurnSessions() {
        var completedSession = buildSession("s-comp", testUser, "tutor", "COMPLETED", 5);
        when(sessionRepository.findByIdAndUserId("s-comp", 1L)).thenReturn(Optional.of(completedSession));

        var req = AiMessageSendRequest.builder().text("Xin chào").build();
        assertThatThrownBy(() -> tutorService.sendMessage("s-comp", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hội thoại này đã kết thúc");

        testSettings.setMaxTurnsPerSession(10);
        var maxTurnSession = buildSession("s-max", testUser, "tutor", "ACTIVE", 10);
        when(sessionRepository.findByIdAndUserId("s-max", 1L)).thenReturn(Optional.of(maxTurnSession));

        assertThatThrownBy(() -> tutorService.sendMessage("s-max", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đạt giới hạn tối đa");
    }

    @Test
    void sendMessageValidatesLengthAndHandlesEmptyBlankOrTooLong() {
        var activeSession = buildSession("s-val", testUser, "tutor", "ACTIVE", 0);
        when(sessionRepository.findByIdAndUserId("s-val", 1L)).thenReturn(Optional.of(activeSession));

        var blankReq = AiMessageSendRequest.builder().text("   ").build();
        assertThatThrownBy(() -> tutorService.sendMessage("s-val", blankReq))
                .isInstanceOf(IllegalArgumentException.class);

        var longReq = AiMessageSendRequest.builder().text("a".repeat(2001)).build();
        assertThatThrownBy(() -> tutorService.sendMessage("s-val", longReq))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessageInvokesAiProviderAndSavesReplyWithUsageStats() {
        var activeSession = buildSession("s-chat", testUser, "tutor", "ACTIVE", 2);
        when(sessionRepository.findByIdAndUserId("s-chat", 1L)).thenReturn(Optional.of(activeSession));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("s-chat")).thenReturn(new ArrayList<>());
        when(intentClassifier.classify(anyString(), anyList())).thenReturn(ConversationIntent.VOCABULARY);

        var geminiResponse = new GeminiApiClient.GeminiResponse("Từ 'phở' là món ăn truyền thống.", 100, 150, 320L, true);
        when(aiProviderService.generateMultiTurn(anyString(), anyList(), anyString()))
                .thenReturn(geminiResponse);

        var request = AiMessageSendRequest.builder()
                .text("Từ phở nghĩa là gì?")
                .mode("tutor")
                .language("vi")
                .build();

        AiMessageResponse response = tutorService.sendMessage("s-chat", request);

        assertThat(response).isNotNull();
        assertThat(response.getText()).contains("Từ 'phở'");
        assertThat(activeSession.getTurnsCount()).isEqualTo(3);

        verify(messageRepository, times(2)).save(any(AiMessage.class)); // saves user message + ai message
        verify(sessionRepository).save(activeSession);
        verify(usageLogRepository).save(any(AiUsageLog.class));
    }

    @Test
    void sendMessageFallsBackToOfflineResponseWhenProviderReturnsNullOrFails() {
        var activeSession = buildSession("s-fallback", testUser, "tutor", "ACTIVE", 1);
        when(sessionRepository.findByIdAndUserId("s-fallback", 1L)).thenReturn(Optional.of(activeSession));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("s-fallback")).thenReturn(new ArrayList<>());
        when(intentClassifier.classify(anyString(), anyList())).thenReturn(ConversationIntent.PRONUNCIATION);

        // When external AI returns null (timeout, network error, invalid key)
        when(aiProviderService.generateMultiTurn(anyString(), anyList(), anyString()))
                .thenReturn(null);

        var request = AiMessageSendRequest.builder()
                .text("Phát âm từ bánh mì thế nào?")
                .mode("tutor")
                .language("vi")
                .build();

        AiMessageResponse response = tutorService.sendMessage("s-fallback", request);

        // System must gracefully respond using offline fallback rather than throwing 500
        assertThat(response).isNotNull();
        assertThat(response.getText()).isNotBlank();
        assertThat(activeSession.getTurnsCount()).isEqualTo(2);

        verify(messageRepository, times(2)).save(any(AiMessage.class)); // saves user message + offline ai message
        verify(usageLogRepository).save(any(AiUsageLog.class));
    }

    @Test
    void endSessionMarksSessionCompletedAndGeneratesSummary() {
        var activeSession = buildSession("s-end", testUser, "tutor", "ACTIVE", 4);
        when(sessionRepository.findByIdAndUserId("s-end", 1L)).thenReturn(Optional.of(activeSession));

        AiSessionResponse response = tutorService.endSession("s-end");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(activeSession.getStatus()).isEqualTo("COMPLETED");
        assertThat(activeSession.getEndedAt()).isNotNull();
        assertThat(activeSession.getFeedbackSummary()).contains("4 lượt trao đổi");
        verify(sessionRepository).save(activeSession);
    }

    @Test
    void submitReviewPersistsReviewAndScoresSession() {
        var completedSession = buildSession("s-rev", testUser, "tutor", "COMPLETED", 3);
        when(sessionRepository.findByIdAndUserId("s-rev", 1L)).thenReturn(Optional.of(completedSession));

        var req = AiReviewCreateRequest.builder()
                .sessionId("s-rev")
                .rating(5)
                .userFeedback("Giải thích rất dễ hiểu")
                .taggedIssues(List.of("helpful", "fast"))
                .build();

        AiReviewResponse response = tutorService.submitReview(req);

        assertThat(response).isNotNull();
        assertThat(response.getRating()).isEqualTo(5);
        verify(reviewRepository).save(any(AiReview.class));
    }

    @Test
    void enrichVocabularyThrowsWhenApiKeyUnavailableAndSucceedsWithValidJson() {
        var req = new AiVocabularyEnrichmentRequest("cảm ơn", "Bài học chào hỏi", "Chào bạn, cảm ơn bạn");

        // When key is unavailable
        when(aiProviderService.isApiKeyAvailable(anyString())).thenReturn(false);
        assertThatThrownBy(() -> tutorService.enrichVocabulary(req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));

        // When key is available and provider returns valid JSON
        when(aiProviderService.isApiKeyAvailable(anyString())).thenReturn(true);
        String jsonPayload = """
                {
                    "word": "cảm ơn",
                    "meaning": "Thank you",
                    "pronunciation": "kahm uhn",
                    "exampleSentence": "Cảm ơn bạn rất nhiều.",
                    "note": "Common polite expression."
                }
                """;
        var dummyRes = new GeminiApiClient.GeminiResponse(jsonPayload, 50, 70, 280L, true);
        when(aiProviderService.generateContent(anyString(), anyString(), anyString())).thenReturn(dummyRes);

        AiVocabularyEnrichmentResponse enrichment = tutorService.enrichVocabulary(req);
        assertThat(enrichment.word()).isEqualTo("cảm ơn");
        assertThat(enrichment.meaning()).isEqualTo("Thank you");
        assertThat(enrichment.pronunciation()).isEqualTo("kahm uhn");
        assertThat(enrichment.exampleSentence()).contains("Cảm ơn bạn");
    }

    // --- Helper Fixtures matching f9fac16 pattern ---

    private static User buildUser(Long id, String username, String email, String targetLevel, String nativeLang) {
        var user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setTargetLevel(targetLevel);
        user.setNativeLanguage(nativeLang);
        user.setLearningGoal("Everyday conversations");
        return user;
    }

    private static AiSetting buildSettings() {
        return AiSetting.builder()
                .id(1L)
                .activeModel("gemini-2.0-flash")
                .dailySessionsPerUser(20)
                .maxTurnsPerSession(25)
                .monthlyBudgetCapUsd(new BigDecimal("100.00"))
                .apiKeyConfigured(true)
                .build();
    }

    private static AiSession buildSession(String id, User user, String mode, String status, int turnsCount) {
        return AiSession.builder()
                .id(id)
                .user(user)
                .mode(mode)
                .title("Hội thoại " + mode)
                .status(status)
                .turnsCount(turnsCount)
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static AiMessage buildMessage(String id, AiSession session, String sender, String text) {
        return AiMessage.builder()
                .id(id)
                .session(session)
                .sender(sender)
                .mode(session.getMode())
                .text(text)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
