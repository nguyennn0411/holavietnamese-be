package com.sep490.backend.modules.ai.service;

import com.sep490.backend.modules.ai.entity.AiMessage;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class IntentClassifier {

    public ConversationIntent classify(String message, List<AiMessage> previousMessages) {
        if (message == null || message.trim().isEmpty()) {
            return ConversationIntent.UNCLEAR;
        }

        String text = normalize(message);

        // 1. Pronunciation (phát âm, cách đọc, đánh vần, pronounce, pronunciation, how to say)
        if (text.matches(".*\\b(phat am|doc tu|cach doc|danh van|pronounce|pronunciation|cach phat am|how to pronounce|how to say)\\b.*")) {
            return ConversationIntent.PRONUNCIATION;
        }

        // 2. Vocabulary meaning (nghĩa là gì, nghĩa của, là gì, ý nghĩa, meaning of, what does ... mean)
        if (text.matches(".*\\b(nghia la gi|nghia cua|la gi|y nghia|meaning of|what does .* mean|define|dinh nghia)\\b.*")) {
            return ConversationIntent.VOCABULARY;
        }

        // 3. Translation (dịch, translate, sang tiếng, dịch sang, how to translate)
        if (text.matches(".*\\b(dich|translate|translate into|dich sang|dich giup|how to say .* in vietnamese)\\b.*")) {
            return ConversationIntent.TRANSLATION;
        }

        // 4. Grammar check / correction (ngữ pháp, sửa câu, đúng không, sai ở đâu, có đúng ngữ pháp, correct this, is this correct)
        if (text.matches(".*\\b(ngu phap|sua cau|dung ngu phap|sai o dau|co dung khong|cau nay dung|correct this|check my sentence|is this correct|am i saying this right)\\b.*")) {
            return ConversationIntent.GRAMMAR_CORRECTION;
        }

        // 5. Practice / exercise (luyện tập, bài tập, kiểm tra tôi, quiz, practice, cho tôi bài tập, đặt câu)
        if (text.matches(".*\\b(luyen tap|bai tap|kiem tra toi|quiz|practice|cho toi bai tap|dat cau|test me|exercise)\\b.*")) {
            return ConversationIntent.PRACTICE;
        }

        // 6. Contextual follow-up ("còn từ đó", "từ đó thì sao", "tại sao", "còn món này", "what about", "why")
        if (text.matches("^(con tu do|tu do thi sao|y ban la gi|tai sao|sao lai the|con nua khong|con mon do|con cau nay|what about|and that|why).*")) {
            return resolveContextualFollowUp(previousMessages);
        }

        // Default to natural casual conversation
        return ConversationIntent.CASUAL_CHAT;
    }

    private ConversationIntent resolveContextualFollowUp(List<AiMessage> previousMessages) {
        if (previousMessages == null || previousMessages.isEmpty()) {
            return ConversationIntent.CASUAL_CHAT;
        }

        // Inspect the most recent messages backwards to infer the running topic
        for (int i = previousMessages.size() - 1; i >= 0; i--) {
            AiMessage prev = previousMessages.get(i);
            String prevText = normalize(prev.getText());

            if (prevText.matches(".*\\b(phat am|doc tu|cach doc|pronounce)\\b.*")) {
                return ConversationIntent.PRONUNCIATION;
            }
            if (prevText.matches(".*\\b(nghia la gi|nghia cua|meaning)\\b.*")) {
                return ConversationIntent.VOCABULARY;
            }
            if (prevText.matches(".*\\b(dich|translate)\\b.*")) {
                return ConversationIntent.TRANSLATION;
            }
            if (prevText.matches(".*\\b(ngu phap|sua cau|correct)\\b.*")) {
                return ConversationIntent.GRAMMAR_CORRECTION;
            }
        }

        return ConversationIntent.CONTEXTUAL_FOLLOW_UP;
    }

    public String normalize(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .trim();
    }
}
