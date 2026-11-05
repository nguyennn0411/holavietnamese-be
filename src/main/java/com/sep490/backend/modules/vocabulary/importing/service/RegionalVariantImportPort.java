package com.sep490.backend.modules.vocabulary.importing.service;

/**
 * Keeps the vocabulary import module independent from Explore persistence.
 * The Explore module implements this port and stores imported variants as drafts.
 */
public interface RegionalVariantImportPort {
    record Variant(String region, String word, String meaning, String meaningEn,
            String pronunciation, String audioUrl, String exampleSentence,
            String exampleSentenceEn, String usageNote, String usageNoteEn) {}

    boolean exists(long vocabularyId, Variant variant);
    void createDraft(long vocabularyId, Variant variant);
}
