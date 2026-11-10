package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.modules.vocabulary.dto.response.VocabularyResponse;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Row;
import com.sep490.backend.modules.vocabulary.importing.service.RegionalVariantImportPort;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyImportHandler;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests for row assembly and validation before an Excel import can write data. */
class VocabularyImportHandlerTests {
    private AdminVocabularyService vocabulary;
    private VocabularyRepository words;
    private RegionalVariantImportPort regional;
    private VocabularyImportHandler handler;

    @BeforeEach
    void setUp() {
        vocabulary = mock(AdminVocabularyService.class);
        words = mock(VocabularyRepository.class);
        regional = mock(RegionalVariantImportPort.class);
        handler = new VocabularyImportHandler(vocabulary, words, regional, mock(Validator.class));
    }

    @Test
    void metadataDeclaresVocabularyLockAndRequiredColumns() {
        assertThat(handler.lockModule()).isEqualTo("VOCABULARY");
        assertThat(handler.type().requiredColumns()).containsExactly("word", "part_of_speech");
        assertThat(handler.type().columns())
                .contains("meaning_order", "example_vi", "region", "regional_word");
    }

    @Test
    void validateReportsDuplicateRowsOrderStatusExampleAndRegionalErrors() {
        var invalid =
                row(
                        2,
                        "word", "xin chào",
                        "part_of_speech", "phrase",
                        "meaning_order", "not-a-number",
                        "translation_en", "hello",
                        "status", "PUBLISHED",
                        "example_en", "Hello!",
                        "region", "WEST",
                        "regional_word", "",
                        "regional_meaning", "");

        // The same values on another row are also a full-row duplicate.
        var errors = handler.validate(List.of(invalid, new Row(3, invalid.values())));

        assertThat(errors).extracting(error -> error.column())
                .contains(
                        "word",              // completely duplicated row
                        "meaning_order",     // non-numeric order
                        "status",            // imports must remain DRAFT
                        "example_vi",        // translation requires Vietnamese example
                        "region",            // only NORTH/CENTRAL/SOUTH
                        "regional_word",     // required when regional data exists
                        "regional_meaning");
    }

    @Test
    void validateDetectsConflictingMergedFieldsAndDuplicateExampleOrder() {
        var first =
                row(
                        2,
                        "word", "xin chào",
                        "part_of_speech", "phrase",
                        "pronunciation", "a",
                        "translation_en", "hello",
                        "meaning_order", "0",
                        "example_vi", "Xin chào",
                        "example_order", "0");
        var second =
                row(
                        3,
                        "word", "xin chào",
                        "part_of_speech", "phrase",
                        "pronunciation", "b",
                        "translation_en", "hello",
                        "meaning_order", "0",
                        "example_vi", "Chào bạn",
                        "example_order", "0");

        assertThat(handler.validate(List.of(first, second)))
                .extracting(error -> error.column())
                .contains("pronunciation", "example_order");
    }

    @Test
    void writeRejectsInvalidWorkbookBeforeAnyDatabaseWrite() {
        var invalid = row(2, "word", "xin", "part_of_speech", "noun", "status", "ARCHIVED");

        assertThatThrownBy(() -> handler.write(List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid vocabulary workbook");
        verify(vocabulary, never()).create(any());
        verify(regional, never()).createDraft(any(Long.class), any());
    }

    @Test
    void writeCreatesDraftWordAndMapsRegionalVariantThenSkipsDuplicates() {
        var input =
                row(
                        2,
                        "word", "quả dứa",
                        "part_of_speech", "noun",
                        "translation_en", "pineapple",
                        "region", "SOUTH",
                        "regional_word", "Thơm",
                        "regional_meaning", "Quả dứa",
                        "regional_meaning_en", "pineapple");
        when(vocabulary.create(any()))
                .thenReturn(
                        new VocabularyResponse(
                                10L, "quả dứa", null, "noun", null, VocabularyStatus.DRAFT,
                                null, null, List.of(), null, null));

        var result = handler.write(List.of(input));

        assertThat(result.importedRows()).isEqualTo(2);
        var variant = ArgumentCaptor.forClass(RegionalVariantImportPort.Variant.class);
        verify(regional).createDraft(org.mockito.ArgumentMatchers.eq(10L), variant.capture());
        assertThat(variant.getValue().region()).isEqualTo("SOUTH");
        assertThat(variant.getValue().word()).isEqualTo("Thơm");

        // On a retry, an existing master and variant are reported but not written again.
        var existing = new VocabularyWord();
        existing.setId(10L);
        existing.setWord("quả dứa");
        existing.setPartOfSpeech("noun");
        when(words.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase("quả dứa", "noun"))
                .thenReturn(List.of(existing));
        when(regional.exists(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(true);
        assertThat(handler.write(List.of(input)).importedRows()).isZero();
    }

    private static Row row(int number, String... keyValues) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(keyValues[index], keyValues[index + 1]);
        }
        return new Row(number, values);
    }
}
