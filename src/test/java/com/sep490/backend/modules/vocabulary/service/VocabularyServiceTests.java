package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.response.DictionaryMetadata;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

/** Isolated tests for the learner-facing dictionary service. */
class VocabularyServiceTests {
    private VocabularyRepository repository;
    private VocabularyService service;

    @BeforeEach
    void setUp() {
        repository = mock(VocabularyRepository.class);
        service = new VocabularyService(repository);
    }

    @Test
    void searchAndDetailNeverExposeUnpublishedWords() {
        var published = word(1L, "xin chào", "phrase", VocabularyStatus.PUBLISHED);
        when(repository.search("xin", VocabularyStatus.PUBLISHED, "phrase"))
                .thenReturn(List.of(published));
        when(repository.findById(1L)).thenReturn(Optional.of(published));
        when(repository.findById(2L))
                .thenReturn(Optional.of(word(2L, "hidden", "noun", VocabularyStatus.DRAFT)));

        assertThat(service.searchPublished("xin", "phrase"))
                .extracting(response -> response.id())
                .containsExactly(1L);
        assertThat(service.findPublishedById(1L).word()).isEqualTo("xin chào");
        assertThatThrownBy(() -> service.findPublishedById(2L))
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.NOT_FOUND));
    }

    @Test
    void searchPageRejectsOversizedKeywordAndDelegatesPublishedFilter() {
        assertThatThrownBy(() -> service.searchPage("x".repeat(201), 0, 20, false))
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.INVALID));

        when(repository.searchPage("xin", VocabularyStatus.PUBLISHED, null, -1, 500, true))
                .thenReturn(new PageImpl<>(List.of(word(1L, "xin", "verb", VocabularyStatus.PUBLISHED))));
        assertThat(service.searchPage("xin", -1, 500, true).getContent()).hasSize(1);
        verify(repository).searchPage("xin", VocabularyStatus.PUBLISHED, null, -1, 500, true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void linksReturnsOtherPartsOfSpeechAndResolvesRelatedWordIds() {
        var source = word(1L, "chào", "verb", VocabularyStatus.PUBLISHED);
        source.setLexicalData(
                DictionaryMetadata.encode(
                        new DictionaryMetadata.Lexical(
                                List.of(),
                                List.of(
                                        new DictionaryMetadata.RelatedWord("xin chào", "related", "greeting"),
                                        new DictionaryMetadata.RelatedWord("vắng", "opposite", null)),
                                List.of(),
                                List.of())));
        var sameSpelling = word(2L, "chào", "noun", VocabularyStatus.PUBLISHED);
        var relatedTarget = word(3L, "xin chào", "phrase", VocabularyStatus.PUBLISHED);

        when(repository.findById(1L)).thenReturn(Optional.of(source));
        when(repository.findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(
                        "chào", VocabularyStatus.PUBLISHED))
                .thenReturn(List.of(source, sameSpelling));
        when(repository.findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(
                        "xin chào", VocabularyStatus.PUBLISHED))
                .thenReturn(List.of(relatedTarget));
        when(repository.findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(
                        "vắng", VocabularyStatus.PUBLISHED))
                .thenReturn(List.of());

        var result = service.links(1L);
        var family = (List<java.util.Map<String, Object>>) result.get("otherPartsOfSpeech");
        var related = (List<java.util.Map<String, Object>>) result.get("relatedWords");

        assertThat(family).singleElement().satisfies(row -> assertThat(row.get("id")).isEqualTo(2L));
        assertThat(related).extracting(row -> row.get("id")).containsExactly(3L, null);
    }

    @Test
    void linksRejectsDraftWord() {
        when(repository.findById(9L))
                .thenReturn(Optional.of(word(9L, "hidden", "noun", VocabularyStatus.DRAFT)));
        assertThatThrownBy(() -> service.links(9L))
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.NOT_FOUND));
    }

    private static VocabularyWord word(
            long id, String text, String part, VocabularyStatus status) {
        var word = new VocabularyWord();
        word.setId(id);
        word.setWord(text);
        word.setPartOfSpeech(part);
        word.setStatus(status);
        return word;
    }
}
