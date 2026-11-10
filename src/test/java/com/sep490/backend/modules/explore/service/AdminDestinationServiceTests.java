package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.DestinationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests for destination normalization, duplicate checks and review workflow. */
class AdminDestinationServiceTests {
    private DestinationRepository repository;
    private AdminDestinationService service;

    @BeforeEach
    void setUp() {
        repository = mock(DestinationRepository.class);
        service = new AdminDestinationService(repository, mock(DestinationExploreService.class));
        when(repository.saveAndFlush(any(Destination.class)))
                .thenAnswer(
                        invocation -> {
                            Destination saved = invocation.getArgument(0);
                            if (saved.getId() == null) saved.setId(100L);
                            return saved;
                        });
    }

    @Test
    void createNormalizesSlugTrimsFieldsAndDefaultsToDraft() {
        var response = service.create(request("  HA-NOI  ", null));

        var saved = ArgumentCaptor.forClass(Destination.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(response.status()).isEqualTo(DestinationStatus.DRAFT);
        assertThat(saved.getValue().getNameVi()).isEqualTo("Hà Nội");
        assertThat(saved.getValue().getSlug()).isEqualTo("ha-noi");
        assertThat(saved.getValue().getShortDescriptionEn()).isNull();
        assertThat(saved.getValue().getDescriptionVi()).isEqualTo("Thủ đô");
    }

    @Test
    void createAndUpdateRejectDuplicateSlug() {
        when(repository.existsBySlug("ha-noi")).thenReturn(true);
        assertKind(() -> service.create(request(" HA-NOI ", null)), LearningException.Kind.CONFLICT);

        when(repository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.DRAFT)));
        when(repository.existsBySlugExceptId("ha-noi", 1L)).thenReturn(true);
        assertKind(() -> service.update(1L, request("ha-noi", null)), LearningException.Kind.CONFLICT);
    }

    @Test
    void draftEndpointsRejectStatusBypass() {
        assertKind(
                () -> service.createDraft(request("slug", DestinationStatus.PUBLISHED)),
                LearningException.Kind.INVALID);

        when(repository.findById(1L))
                .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
        assertKind(
                () -> service.updateDraft(1L, request("slug", DestinationStatus.DRAFT)),
                LearningException.Kind.CONFLICT);

        when(repository.findById(2L)).thenReturn(Optional.of(destination(2L, DestinationStatus.DRAFT)));
        assertKind(
                () -> service.updateDraft(2L, request("slug", DestinationStatus.PUBLISHED)),
                LearningException.Kind.INVALID);
    }

    @Test
    void workflowTransitionsRecordPublicationAndArchiveTimes() {
        when(repository.findById(1L)).thenReturn(Optional.of(destination(1L, DestinationStatus.DRAFT)));
        assertThat(service.submitReview(1L).status()).isEqualTo(DestinationStatus.PENDING_REVIEW);

        when(repository.findById(2L))
                .thenReturn(Optional.of(destination(2L, DestinationStatus.PENDING_REVIEW)));
        var published = service.review(2L, ContentReviewRequest.Decision.APPROVE);
        assertThat(published.status()).isEqualTo(DestinationStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();

        when(repository.findById(3L))
                .thenReturn(Optional.of(destination(3L, DestinationStatus.PUBLISHED)));
        var archived = service.archive(3L);
        assertThat(archived.status()).isEqualTo(DestinationStatus.ARCHIVED);
        assertThat(archived.archivedAt()).isNotNull();
    }

    @Test
    void rejectRestoreAndReviseReturnContentToDraft() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(destination(1L, DestinationStatus.PENDING_REVIEW)));
        when(repository.findById(2L))
                .thenReturn(Optional.of(destination(2L, DestinationStatus.ARCHIVED)));
        when(repository.findById(3L))
                .thenReturn(Optional.of(destination(3L, DestinationStatus.PUBLISHED)));

        assertThat(service.review(1L, ContentReviewRequest.Decision.REJECT).status())
                .isEqualTo(DestinationStatus.DRAFT);
        assertThat(service.restore(2L).status()).isEqualTo(DestinationStatus.DRAFT);
        assertThat(service.revise(3L).status()).isEqualTo(DestinationStatus.DRAFT);
    }

    @Test
    void invalidTransitionsAndRepeatedArchiveDoNotWriteUnexpectedly() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(destination(1L, DestinationStatus.PENDING_REVIEW)));
        assertKind(() -> service.archive(1L), LearningException.Kind.CONFLICT);

        var archived = destination(2L, DestinationStatus.ARCHIVED);
        when(repository.findById(2L)).thenReturn(Optional.of(archived));
        assertThat(service.archive(2L).status()).isEqualTo(DestinationStatus.ARCHIVED);
        verify(repository, never()).saveAndFlush(archived);

        when(repository.findById(3L)).thenReturn(Optional.of(destination(3L, DestinationStatus.DRAFT)));
        assertKind(() -> service.restore(3L), LearningException.Kind.CONFLICT);
        assertKind(() -> service.revise(3L), LearningException.Kind.CONFLICT);
        assertKind(
                () -> service.review(3L, ContentReviewRequest.Decision.APPROVE),
                LearningException.Kind.CONFLICT);
    }

    private static DestinationRequest request(String slug, DestinationStatus status) {
        return new DestinationRequest(
                " Hà Nội ", " Hanoi ", slug, VietnamRegion.NORTH,
                " Thủ đô Việt Nam ", "   ", " Thủ đô ", null, " ", status);
    }

    private static Destination destination(long id, DestinationStatus status) {
        var destination = new Destination();
        destination.setId(id);
        destination.setNameVi("Hà Nội");
        destination.setNameEn("Hanoi");
        destination.setSlug("ha-noi-" + id);
        destination.setRegion(VietnamRegion.NORTH);
        destination.setStatus(status);
        return destination;
    }

    private static void assertKind(Runnable action, LearningException.Kind expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(expected));
    }
}
