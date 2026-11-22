package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.RegionalVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Unit tests for public regional comparison and destination filtering. */
class RegionalVocabularyServiceTests {
    private RegionalVocabularyRepository repository;
    private RegionalVocabularyService service;

    @BeforeEach
    void setUp() {
        repository = mock(RegionalVocabularyRepository.class);
        service = new RegionalVocabularyService(repository);
    }

    @Test
    void comparisonsSelectMatchingMastersThenIncludeAllTheirPublishedVariants() {
        var masterOne = master(1L, "quả dứa");
        var masterTwo = master(2L, "cha");
        var south = variant(11L, masterOne, VietnamRegion.SOUTH, "Thơm");
        var north = variant(12L, masterOne, VietnamRegion.NORTH, "Dứa");
        var unrelated = variant(21L, masterTwo, VietnamRegion.SOUTH, "Ba");

        // First search matches master 1; the second query is constrained to that master.
        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(south), List.of(south, north));

        var result = service.comparisons("thơm", VietnamRegion.SOUTH);

        assertThat(result).singleElement()
                .satisfies(
                        comparison -> {
                            assertThat(comparison.vocabularyId()).isEqualTo(1L);
                            assertThat(comparison.word()).isEqualTo("quả dứa");
                            assertThat(comparison.variants()).extracting(v -> v.word())
                                    .containsExactly("Thơm", "Dứa");
                        });
    }

    @Test
    void comparisonReturnsAllVariantsOrNotFound() {
        var master = master(1L, "quả dứa");
        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(variant(11L, master, VietnamRegion.SOUTH, "Thơm")))
                .thenReturn(List.of());

        assertThat(service.comparison(1L).variants()).hasSize(1);
        assertThatThrownBy(() -> service.comparison(99L))
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.NOT_FOUND));
    }

    @Test
    void forDestinationShortCircuitsEmptyIdsAndFiltersRequestedMasters() {
        assertThat(service.forDestination(List.of(), VietnamRegion.SOUTH)).isEmpty();
        verify(repository, never()).findAll(any(Specification.class), any(Sort.class));

        var masterOne = master(1L, "quả dứa");
        var masterTwo = master(2L, "cha");
        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(variant(21L, masterTwo, VietnamRegion.SOUTH, "Ba")));

        assertThat(service.forDestination(List.of(2L), VietnamRegion.SOUTH))
                .extracting(response -> response.vocabularyId())
                .containsExactly(2L);
    }

    @Test
    void comparisonPagePagesMastersAndBatchLoadsTheirVariants() {
        var masterOne = master(1L, "quả dứa");
        when(repository.findPublishedMasterIds(
                        org.mockito.ArgumentMatchers.eq("thơm"),
                        org.mockito.ArgumentMatchers.eq(VietnamRegion.SOUTH),
                        org.mockito.ArgumentMatchers.eq(VocabularyStatus.PUBLISHED),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(1L)));
        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(variant(11L, masterOne, VietnamRegion.SOUTH, "Thơm")));

        var result = service.comparisonPage(" Thơm ", VietnamRegion.SOUTH, 0, 20);

        assertThat(result.getContent()).singleElement()
                .satisfies(row -> assertThat(row.vocabularyId()).isEqualTo(1L));
    }

    private static VocabularyWord master(long id, String text) {
        var master = new VocabularyWord();
        master.setId(id);
        master.setWord(text);
        master.setPartOfSpeech("noun");
        master.setStatus(VocabularyStatus.PUBLISHED);
        return master;
    }

    private static RegionalVocabulary variant(
            long id, VocabularyWord master, VietnamRegion region, String text) {
        var row = new RegionalVocabulary();
        row.setId(id);
        row.setVocabulary(master);
        row.setRegion(region);
        row.setWord(text);
        row.setMeaning("meaning");
        row.setStatus(VocabularyStatus.PUBLISHED);
        return row;
    }
}
