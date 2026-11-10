package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyExampleRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyRequest;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;

/** Unit tests for validation, mapping and every admin vocabulary state transition. */
class AdminVocabularyServiceTests {
    private VocabularyRepository repository;
    private JdbcTemplate jdbc;
    private AdminVocabularyService service;

    @BeforeEach
    void setUp() {
        repository = mock(VocabularyRepository.class);
        jdbc = mock(JdbcTemplate.class);
        service = new AdminVocabularyService(repository, jdbc);

        // Return the same entity to emulate saveAndFlush without starting Spring/JPA.
        when(repository.saveAndFlush(any(VocabularyWord.class)))
                .thenAnswer(
                        invocation -> {
                            VocabularyWord saved = invocation.getArgument(0);
                            if (saved.getId() == null) saved.setId(100L);
                            return saved;
                        });
    }

    @Test
    void createNormalizesFieldsMapsNestedContentAndDefaultsToDraft() {
        var request =
                new VocabularyRequest(
                        "  Xin   chào  ",
                        "   ",
                        " phrase ",
                        " /audio.mp3 ",
                        null,
                        List.of(
                                new VocabularyMeaningRequest(
                                        " hello ",
                                        " greeting ",
                                        "   ",
                                        null,
                                        List.of(
                                                new VocabularyExampleRequest(
                                                        " Xin chào! ", " Hello! ", " ", null)),
                                        " lời chào ",
                                        List.of("formal"))));

        var response = service.create(request);

        var saved = ArgumentCaptor.forClass(VocabularyWord.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(response.status()).isEqualTo(VocabularyStatus.DRAFT);
        assertThat(saved.getValue().getWord()).isEqualTo("Xin chào");
        assertThat(saved.getValue().getSearchWord()).isEqualTo("xin chao");
        assertThat(saved.getValue().getPronunciation()).isNull();
        assertThat(saved.getValue().getAudioUrl()).isEqualTo("/audio.mp3");
        assertThat(saved.getValue().getMeanings()).singleElement()
                .satisfies(
                        meaning -> {
                            assertThat(meaning.getTranslationEn()).isEqualTo("hello");
                            assertThat(meaning.getUsageNote()).isNull();
                            assertThat(meaning.getExamples()).singleElement()
                                    .satisfies(example -> assertThat(example.getExampleVi()).isEqualTo("Xin chào!"));
                        });
    }

    @Test
    void createRejectsAccentSensitiveDuplicateButAllowsDifferentVietnameseWord() {
        var existing = word(9L, "Thơm", VocabularyStatus.DRAFT);
        when(repository.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase("Thơm", "noun"))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(request(" Thơm ", VocabularyStatus.DRAFT)))
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.CONFLICT));
        verify(repository, never()).saveAndFlush(any(VocabularyWord.class));

        // The explicit normalized comparison protects Vietnamese words that differ by accents.
        when(repository.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase("Thom", "noun"))
                .thenReturn(List.of(existing));
        assertThat(service.create(request("Thom", VocabularyStatus.DRAFT)).word()).isEqualTo("Thom");
    }

    @Test
    void createDraftAndUpdateDraftRejectStatusBypass() {
        assertKind(
                () -> service.createDraft(request("word", VocabularyStatus.PUBLISHED)),
                LearningException.Kind.INVALID);

        when(repository.findById(1L)).thenReturn(Optional.of(word(1L, "word", VocabularyStatus.PUBLISHED)));
        assertKind(
                () -> service.updateDraft(1L, request("word", VocabularyStatus.DRAFT)),
                LearningException.Kind.CONFLICT);

        when(repository.findById(2L)).thenReturn(Optional.of(word(2L, "word", VocabularyStatus.DRAFT)));
        assertKind(
                () -> service.updateDraft(2L, request("word", VocabularyStatus.PUBLISHED)),
                LearningException.Kind.INVALID);
    }

    @Test
    void submitRestoreAndReviseEnforceTheirSourceStates() {
        when(repository.findById(1L)).thenReturn(Optional.of(word(1L, "word", VocabularyStatus.DRAFT)));
        assertThat(service.submitReview(1L).status()).isEqualTo(VocabularyStatus.PENDING_REVIEW);

        when(repository.findById(2L)).thenReturn(Optional.of(word(2L, "word", VocabularyStatus.ARCHIVED)));
        assertThat(service.restore(2L).status()).isEqualTo(VocabularyStatus.DRAFT);

        when(repository.findById(3L)).thenReturn(Optional.of(word(3L, "word", VocabularyStatus.PUBLISHED)));
        assertThat(service.revise(3L).status()).isEqualTo(VocabularyStatus.DRAFT);

        when(repository.findById(4L)).thenReturn(Optional.of(word(4L, "word", VocabularyStatus.DRAFT)));
        assertKind(() -> service.restore(4L), LearningException.Kind.CONFLICT);
        assertKind(() -> service.revise(4L), LearningException.Kind.CONFLICT);
    }

    @Test
    void archiveRejectsPendingAndIsIdempotentForArchivedWord() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(word(1L, "word", VocabularyStatus.PENDING_REVIEW)));
        assertKind(() -> service.archive(1L), LearningException.Kind.CONFLICT);

        var archived = word(2L, "word", VocabularyStatus.ARCHIVED);
        when(repository.findById(2L)).thenReturn(Optional.of(archived));
        assertThat(service.archive(2L).status()).isEqualTo(VocabularyStatus.ARCHIVED);

        // No second database write is needed for an already archived word.
        verify(repository, never()).saveAndFlush(archived);
    }

    @Test
    void reviewApprovesOrRejectsOnlyPendingWords() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(word(1L, "approve", VocabularyStatus.PENDING_REVIEW)));
        when(repository.findById(2L))
                .thenReturn(Optional.of(word(2L, "reject", VocabularyStatus.PENDING_REVIEW)));
        when(repository.findById(3L))
                .thenReturn(Optional.of(word(3L, "draft", VocabularyStatus.DRAFT)));

        assertThat(service.review(1L, ContentReviewRequest.Decision.APPROVE).status())
                .isEqualTo(VocabularyStatus.PUBLISHED);
        assertThat(service.review(2L, ContentReviewRequest.Decision.REJECT).status())
                .isEqualTo(VocabularyStatus.DRAFT);
        assertKind(
                () -> service.review(3L, ContentReviewRequest.Decision.APPROVE),
                LearningException.Kind.CONFLICT);
    }

    @Test
    void searchPageValidatesKeywordAndReferenceLookupsDelegateToRepository() {
        assertKind(
                () -> service.searchPage("x".repeat(201), null, null, 0, 20, false),
                LearningException.Kind.INVALID);

        when(repository.searchPage("xin", VocabularyStatus.DRAFT, "noun", -1, 500, true))
                .thenReturn(new PageImpl<>(List.of(word(1L, "xin", VocabularyStatus.DRAFT))));
        assertThat(service.searchPage("xin", VocabularyStatus.DRAFT, "noun", -1, 500, true))
                .hasSize(1);

        var row = word(5L, "five", VocabularyStatus.DRAFT);
        when(repository.findById(5L)).thenReturn(Optional.of(row));
        when(repository.findAllById(List.of(5L))).thenReturn(List.of(row));
        assertThat(service.findExistingWord(5L)).contains(row);
        assertThat(service.findExistingWords(List.of(5L))).containsExactly(row);
    }

    private static VocabularyRequest request(String text, VocabularyStatus status) {
        return new VocabularyRequest(
                text,
                null,
                "noun",
                null,
                status,
                List.of(new VocabularyMeaningRequest("meaning", null, null, 0, List.of())));
    }

    private static VocabularyWord word(long id, String text, VocabularyStatus status) {
        var word = new VocabularyWord();
        word.setId(id);
        word.setWord(text);
        word.setPartOfSpeech("noun");
        word.setStatus(status);
        return word;
    }

    private static void assertKind(Runnable action, LearningException.Kind expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(expected));
    }
}
