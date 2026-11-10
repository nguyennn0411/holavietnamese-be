package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.RegionalVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Unit tests for regional vocabulary validation and the complete review state machine. */
class AdminRegionalVocabularyServiceTests {
    private RegionalVocabularyRepository repository;
    private AdminVocabularyService masterWords;
    private AdminRegionalVocabularyService service;

    @BeforeEach
    void setUp() {
        repository = mock(RegionalVocabularyRepository.class);
        masterWords = mock(AdminVocabularyService.class);
        service = new AdminRegionalVocabularyService(repository, masterWords);
        when(repository.saveAndFlush(any(RegionalVocabulary.class)))
                .thenAnswer(
                        invocation -> {
                            RegionalVocabulary saved = invocation.getArgument(0);
                            if (saved.getId() == null) saved.setId(100L);
                            return saved;
                        });
    }

    @Test
    void searchPageClampsNegativePageAndOversizedPageSize() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchPage("thơm", VietnamRegion.SOUTH, 1L, VocabularyStatus.DRAFT, -3, 500);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("vocabulary.word")).isNotNull();
    }

    @Test
    void createRejectsMissingMasterAndNormalizedDuplicate() {
        when(masterWords.findExistingWord(99L)).thenReturn(Optional.empty());
        assertKind(() -> service.create(request(99L, "Thơm", VocabularyStatus.DRAFT)),
                LearningException.Kind.NOT_FOUND);

        var master = master(1L, "quả dứa");
        when(masterWords.findExistingWord(1L)).thenReturn(Optional.of(master));
        var duplicate = variant(9L, master, VocabularyStatus.DRAFT);
        duplicate.setWord("Thơm");
        when(repository.findByVocabularyIdAndRegion(1L, VietnamRegion.SOUTH))
                .thenReturn(List.of(duplicate));

        assertKind(() -> service.create(request(1L, " thơm ", VocabularyStatus.DRAFT)),
                LearningException.Kind.CONFLICT);
        verify(repository, never()).saveAndFlush(any(RegionalVocabulary.class));
    }

    @Test
    void createTrimsRequiredFieldsAndTurnsBlankOptionalFieldsIntoNull() {
        var master = master(1L, "quả dứa");
        when(masterWords.findExistingWord(1L)).thenReturn(Optional.of(master));
        when(repository.findByVocabularyIdAndRegion(1L, VietnamRegion.SOUTH))
                .thenReturn(List.of());

        var response = service.create(request(1L, "  Thơm  ", null));

        var saved = ArgumentCaptor.forClass(RegionalVocabulary.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(response.id()).isEqualTo(100L);
        assertThat(saved.getValue().getWord()).isEqualTo("Thơm");
        assertThat(saved.getValue().getMeaning()).isEqualTo("Pineapple");
        assertThat(saved.getValue().getMeaningEn()).isNull();
        assertThat(saved.getValue().getPronunciation()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(VocabularyStatus.DRAFT);
    }

    @Test
    void draftEndpointsRejectDirectStatusChanges() {
        assertKind(
                () -> service.createDraft(request(1L, "Thơm", VocabularyStatus.PUBLISHED)),
                LearningException.Kind.INVALID);

        var master = master(1L, "quả dứa");
        when(repository.findById(1L))
                .thenReturn(Optional.of(variant(1L, master, VocabularyStatus.PUBLISHED)));
        assertKind(
                () -> service.updateDraft(1L, request(1L, "Thơm", VocabularyStatus.DRAFT)),
                LearningException.Kind.CONFLICT);

        when(repository.findById(2L))
                .thenReturn(Optional.of(variant(2L, master, VocabularyStatus.DRAFT)));
        assertKind(
                () -> service.updateDraft(2L, request(1L, "Thơm", VocabularyStatus.PUBLISHED)),
                LearningException.Kind.INVALID);
    }

    @Test
    void submitApproveRejectArchiveRestoreAndReviseFollowStateRules() {
        var master = master(1L, "quả dứa");
        when(repository.findById(1L))
                .thenReturn(Optional.of(variant(1L, master, VocabularyStatus.DRAFT)));
        assertThat(service.submitReview(1L).status()).isEqualTo(VocabularyStatus.PENDING_REVIEW);

        when(repository.findById(2L))
                .thenReturn(Optional.of(variant(2L, master, VocabularyStatus.PENDING_REVIEW)));
        assertThat(service.review(2L, ContentReviewRequest.Decision.APPROVE).status())
                .isEqualTo(VocabularyStatus.PUBLISHED);

        when(repository.findById(3L))
                .thenReturn(Optional.of(variant(3L, master, VocabularyStatus.PENDING_REVIEW)));
        assertThat(service.review(3L, ContentReviewRequest.Decision.REJECT).status())
                .isEqualTo(VocabularyStatus.DRAFT);

        when(repository.findById(4L))
                .thenReturn(Optional.of(variant(4L, master, VocabularyStatus.PUBLISHED)));
        assertThat(service.archive(4L).status()).isEqualTo(VocabularyStatus.ARCHIVED);

        when(repository.findById(5L))
                .thenReturn(Optional.of(variant(5L, master, VocabularyStatus.ARCHIVED)));
        assertThat(service.restore(5L).status()).isEqualTo(VocabularyStatus.DRAFT);

        when(repository.findById(6L))
                .thenReturn(Optional.of(variant(6L, master, VocabularyStatus.PUBLISHED)));
        assertThat(service.revise(6L).status()).isEqualTo(VocabularyStatus.DRAFT);
    }

    @Test
    void invalidTransitionsAreRejectedWithoutSaving() {
        var master = master(1L, "quả dứa");
        when(repository.findById(1L))
                .thenReturn(Optional.of(variant(1L, master, VocabularyStatus.PENDING_REVIEW)));
        assertKind(() -> service.submitReview(1L), LearningException.Kind.CONFLICT);
        assertKind(() -> service.archive(1L), LearningException.Kind.CONFLICT);

        when(repository.findById(2L))
                .thenReturn(Optional.of(variant(2L, master, VocabularyStatus.DRAFT)));
        assertKind(() -> service.restore(2L), LearningException.Kind.CONFLICT);
        assertKind(() -> service.revise(2L), LearningException.Kind.CONFLICT);
        assertKind(
                () -> service.review(2L, ContentReviewRequest.Decision.APPROVE),
                LearningException.Kind.CONFLICT);
    }

    @Test
    void approveAllDelegatesOnlyPendingToPublishedBulkUpdate() {
        when(repository.approveAllPending(
                        eq(VocabularyStatus.PENDING_REVIEW),
                        eq(VocabularyStatus.PUBLISHED),
                        any(Instant.class)))
                .thenReturn(4);

        assertThat(service.approveAllPending()).isEqualTo(4);
        verify(repository).approveAllPending(
                eq(VocabularyStatus.PENDING_REVIEW),
                eq(VocabularyStatus.PUBLISHED),
                any(Instant.class));
    }

    private static RegionalVocabularyRequest request(
            long masterId, String word, VocabularyStatus status) {
        return new RegionalVocabularyRequest(
                masterId, VietnamRegion.SOUTH, word, "  Pineapple  ", "   ", " ",
                null, null, null, null, null, status);
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
            long id, VocabularyWord master, VocabularyStatus status) {
        var row = new RegionalVocabulary();
        row.setId(id);
        row.setVocabulary(master);
        row.setRegion(VietnamRegion.SOUTH);
        row.setWord("Thơm");
        row.setMeaning("Pineapple");
        row.setStatus(status);
        return row;
    }

    private static void assertKind(Runnable action, LearningException.Kind expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(expected));
    }
}
