package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.RegionalVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.importing.service.RegionalVariantImportPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Verifies the boundary between vocabulary import and Explore regional content. */
class RegionalVariantImportAdapterTests {
    private RegionalVocabularyRepository repository;
    private AdminRegionalVocabularyService regionalService;
    private RegionalVariantImportAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(RegionalVocabularyRepository.class);
        regionalService = mock(AdminRegionalVocabularyService.class);
        adapter = new RegionalVariantImportAdapter(repository, regionalService);
    }

    @Test
    void existsNormalizesRegionCaseWhitespaceAndVietnameseUnicode() {
        var existing = new RegionalVocabulary();
        existing.setWord("Thơm");
        when(repository.findByVocabularyIdAndRegion(10L, VietnamRegion.SOUTH))
                .thenReturn(List.of(existing));

        var variant = variant("south", "  Thơm  ");

        assertThat(adapter.exists(10L, variant)).isTrue();
        verify(repository).findByVocabularyIdAndRegion(10L, VietnamRegion.SOUTH);
    }

    @Test
    void createDraftMapsEveryImportedFieldAndForcesDraftStatus() {
        var variant =
                new RegionalVariantImportPort.Variant(
                        "central", "Mô", "đâu", "where", "mo", "/audio.mp3",
                        "Đi mô?", "Where?", "Dùng ở miền Trung", "Central usage");

        adapter.createDraft(22L, variant);

        var request = ArgumentCaptor.forClass(RegionalVocabularyRequest.class);
        verify(regionalService).createDraft(request.capture());
        assertThat(request.getValue())
                .satisfies(
                        value -> {
                            assertThat(value.vocabularyId()).isEqualTo(22L);
                            assertThat(value.region()).isEqualTo(VietnamRegion.CENTRAL);
                            assertThat(value.word()).isEqualTo("Mô");
                            assertThat(value.meaningEn()).isEqualTo("where");
                            assertThat(value.exampleSentenceEn()).isEqualTo("Where?");
                            assertThat(value.status()).isEqualTo(VocabularyStatus.DRAFT);
                        });
    }

    private static RegionalVariantImportPort.Variant variant(String region, String word) {
        return new RegionalVariantImportPort.Variant(
                region, word, "meaning", null, null, null, null, null, null, null);
    }
}
