package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.vocabulary.dto.response.VocabularyMeaningResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the text rules shared by search, import and the learner notebook. */
class VocabularyTextTests {

    @Test
    void normalizeHandlesNullUnicodeAndRepeatedWhitespace() {
        // Null is deliberately preserved so callers can distinguish a missing value.
        assertThat(VocabularyText.normalize(null)).isNull();

        // a + combining acute accent must become the single NFC character "á".
        String decomposed = "a\u0301";
        assertThat(VocabularyText.normalize("  " + decomposed + "   xin\tchào  "))
                .isEqualTo("á xin chào");
    }

    @Test
    void searchKeyFoldsVietnameseAccentsAndLetterD() {
        // Search is case-insensitive and accent-insensitive, including the special letter đ.
        assertThat(VocabularyText.searchKey("  Điện   Biên  ")).isEqualTo("dien bien");
        assertThat(VocabularyText.searchKey(null)).isEmpty();
    }

    @Test
    void notebookMeaningUsesFallbackOrderAndLengthLimit() {
        // translationEn has the highest priority.
        assertThat(VocabularyText.notebookMeaning(meaning(" pineapple ", "English", "quả dứa")))
                .isEqualTo("pineapple");

        // A blank translation falls back to the Vietnamese definition, then English definition.
        assertThat(VocabularyText.notebookMeaning(meaning(" ", "English", " quả dứa ")))
                .isEqualTo("quả dứa");
        assertThat(VocabularyText.notebookMeaning(meaning(null, " definition ", null)))
                .isEqualTo("definition");
        assertThat(VocabularyText.notebookMeaning(meaning(null, null, null))).isEmpty();

        // Notebook meanings are capped at the database column limit.
        assertThat(VocabularyText.notebookMeaning(meaning("a".repeat(1_005), null, null)))
                .hasSize(1_000);
    }

    private static VocabularyMeaningResponse meaning(
            String translationEn, String definitionEn, String definitionVi) {
        return new VocabularyMeaningResponse(
                null, translationEn, definitionEn, null, 0, List.of(), definitionVi, List.of());
    }
}
