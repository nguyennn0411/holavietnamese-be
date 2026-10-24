package com.sep490.backend;

import com.sep490.backend.entity.AiMessage;
import com.sep490.backend.service.ConversationIntent;
import com.sep490.backend.service.IntentClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentClassifierTest {

    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new IntentClassifier();
    }

    @Test
    @DisplayName("Classifies pronunciation requests correctly")
    void testPronunciationIntent() {
        assertEquals(ConversationIntent.PRONUNCIATION, classifier.classify("Phát âm từ cua", Collections.emptyList()));
        assertEquals(ConversationIntent.PRONUNCIATION, classifier.classify("Cách đọc từ này thế nào?", Collections.emptyList()));
        assertEquals(ConversationIntent.PRONUNCIATION, classifier.classify("How to pronounce bánh mì?", Collections.emptyList()));
    }

    @Test
    @DisplayName("Classifies vocabulary requests correctly")
    void testVocabularyIntent() {
        assertEquals(ConversationIntent.VOCABULARY, classifier.classify("Cua nghĩa là gì?", Collections.emptyList()));
        assertEquals(ConversationIntent.VOCABULARY, classifier.classify("Ý nghĩa của từ phở", Collections.emptyList()));
        assertEquals(ConversationIntent.VOCABULARY, classifier.classify("What does ngon mean?", Collections.emptyList()));
    }

    @Test
    @DisplayName("Classifies translation requests correctly")
    void testTranslationIntent() {
        assertEquals(ConversationIntent.TRANSLATION, classifier.classify("Dịch câu này sang tiếng Anh giúp tôi", Collections.emptyList()));
        assertEquals(ConversationIntent.TRANSLATION, classifier.classify("Translate hello into Vietnamese", Collections.emptyList()));
    }

    @Test
    @DisplayName("Classifies grammar correction requests correctly")
    void testGrammarCorrectionIntent() {
        assertEquals(ConversationIntent.GRAMMAR_CORRECTION, classifier.classify("Câu này đúng ngữ pháp không?", Collections.emptyList()));
        assertEquals(ConversationIntent.GRAMMAR_CORRECTION, classifier.classify("Tôi có 20 tuổi sai ở đâu?", Collections.emptyList()));
        assertEquals(ConversationIntent.GRAMMAR_CORRECTION, classifier.classify("Check my sentence please", Collections.emptyList()));
    }

    @Test
    @DisplayName("Classifies practice exercise requests correctly")
    void testPracticeIntent() {
        assertEquals(ConversationIntent.PRACTICE, classifier.classify("Cho tôi bài tập luyện tập tiếng Việt", Collections.emptyList()));
        assertEquals(ConversationIntent.PRACTICE, classifier.classify("Kiểm tra tôi về từ vựng", Collections.emptyList()));
    }

    @Test
    @DisplayName("Classifies casual chat naturally")
    void testCasualChatIntent() {
        assertEquals(ConversationIntent.CASUAL_CHAT, classifier.classify("Hôm nay mình đi ăn phở với bạn", Collections.emptyList()));
        assertEquals(ConversationIntent.CASUAL_CHAT, classifier.classify("Thời tiết Hà Nội hôm nay đẹp quá", Collections.emptyList()));
        assertEquals(ConversationIntent.CASUAL_CHAT, classifier.classify("Chào bạn nhé", Collections.emptyList()));
    }

    @Test
    @DisplayName("Resolves contextual follow-ups using conversation history")
    void testContextualFollowUp() {
        AiMessage prevPronunciation = AiMessage.builder()
                .sender("user")
                .text("Làm sao để phát âm từ sữa?")
                .build();

        // When user asks "Còn từ đó thì sao?" after pronunciation discussion -> resolves to PRONUNCIATION
        assertEquals(ConversationIntent.PRONUNCIATION, classifier.classify("Còn từ đó thì sao?", List.of(prevPronunciation)));

        AiMessage prevVocab = AiMessage.builder()
                .sender("user")
                .text("Từ duyên dáng nghĩa là gì?")
                .build();

        // When user asks "Còn từ đó thì sao?" after vocabulary discussion -> resolves to VOCABULARY
        assertEquals(ConversationIntent.VOCABULARY, classifier.classify("Còn từ đó thì sao?", List.of(prevVocab)));
    }
}
