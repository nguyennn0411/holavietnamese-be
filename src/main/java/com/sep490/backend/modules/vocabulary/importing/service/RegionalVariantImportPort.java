package com.sep490.backend.modules.vocabulary.importing.service;

/**
 * Cho phep nhap bien the vung mien thong qua module Explore. Explore luu cac bien the duoc nhap o
 * trang thai ban nhap.
 */
public interface RegionalVariantImportPort {
    record Variant(
            String region,
            String word,
            String meaning,
            String meaningEn,
            String pronunciation,
            String audioUrl,
            String exampleSentence,
            String exampleSentenceEn,
            String usageNote,
            String usageNoteEn) {}

    boolean exists(long vocabularyId, Variant variant);

    void createDraft(long vocabularyId, Variant variant);
}
