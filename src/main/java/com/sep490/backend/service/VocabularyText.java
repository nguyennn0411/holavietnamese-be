package com.sep490.backend.service;

import com.sep490.backend.dto.model.VocabularyMeaning;
import java.text.Normalizer;
import java.util.Locale;

public final class VocabularyText {
    private VocabularyText() {}
    public static String normalize(String text) {
        return text == null ? null : Normalizer.normalize(text,Normalizer.Form.NFC).replaceAll("[\\p{Z}\\s]+"," ").strip();
    }
    public static String searchKey(String text) {
        var value = normalize(text);
        return value == null ? "" : Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").replace('đ','d');
    }
    public static String notebookMeaning(VocabularyMeaning meaning) {
        String text = meaning.translationEn();
        if (text == null || text.isBlank()) text = meaning.definitionVi();
        if (text == null || text.isBlank()) text = meaning.definitionEn();
        return text == null ? "" : text.strip().substring(0,Math.min(text.strip().length(),1000));
    }
}
