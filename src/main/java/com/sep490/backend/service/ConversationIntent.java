package com.sep490.backend.service;

/**
 * ConversationIntent - Detected intent behind the learner's message.
 * Enables the AI Tutor to adapt naturally instead of forcing grammar lessons.
 */
public enum ConversationIntent {
    CASUAL_CHAT,
    PRONUNCIATION,
    VOCABULARY,
    TRANSLATION,
    GRAMMAR_CORRECTION,
    PRACTICE,
    CONTEXTUAL_FOLLOW_UP,
    UNCLEAR
}
