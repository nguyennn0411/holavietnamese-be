package com.sep490.backend.modules.vocabulary.service;

import com.sep490.backend.modules.vocabulary.dto.response.VocabularyMeaningResponse;

import java.text.Normalizer;
import java.util.Locale;

public final class VocabularyText {
    private VocabularyText() {}

    /** Chuan hoa Unicode va khoang trang, giu nguyen dau tieng Viet. */
    public static String normalize(String text) {
        return text == null
                ? null
                : Normalizer.normalize(text, Normalizer.Form.NFC)
                        .replaceAll("[\\p{Z}\\s]+", " ")
                        .strip();
    }

    /** Tao khoa tim kiem khong dau de ho tro tra cuu tieng Viet. */
    public static String searchKey(String text) {
        var value = normalize(text);
        return value == null
                ? ""
                : Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replace('đ', 'd');
    }

    /** Chon nghia hien thi theo thu tu uu tien va gioi han do dai so tu. */
    public static String notebookMeaning(VocabularyMeaningResponse meaning) {
        String text = meaning.translationEn();
        if (text == null || text.isBlank()) text = meaning.definitionVi();
        if (text == null || text.isBlank()) text = meaning.definitionEn();
        return text == null ? "" : text.strip().substring(0, Math.min(text.strip().length(), 1000));
    }
}
