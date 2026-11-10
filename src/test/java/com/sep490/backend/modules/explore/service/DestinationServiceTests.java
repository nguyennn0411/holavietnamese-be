package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.DestinationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the public destination facade. */
class DestinationServiceTests {
    private DestinationRepository repository;
    private DestinationService service;

    @BeforeEach
    void setUp() {
        repository = mock(DestinationRepository.class);
        service = new DestinationService(repository);
    }

    @Test
    void searchAlwaysAddsPublishedStatusFilter() {
        when(repository.search("Hà", DestinationStatus.PUBLISHED, VietnamRegion.NORTH))
                .thenReturn(List.of(destination(1L, DestinationStatus.PUBLISHED)));

        assertThat(service.searchPublished("Hà", VietnamRegion.NORTH)).hasSize(1);
        verify(repository).search("Hà", DestinationStatus.PUBLISHED, VietnamRegion.NORTH);
    }

    @Test
    void detailReturnsPublishedDestinationAndHidesDraftOrMissingRows() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(destination(1L, DestinationStatus.PUBLISHED)));
        when(repository.findById(2L))
                .thenReturn(Optional.of(destination(2L, DestinationStatus.DRAFT)));
        when(repository.findById(3L)).thenReturn(Optional.empty());

        assertThat(service.findPublishedById(1L).id()).isEqualTo(1L);
        for (long hiddenId : List.of(2L, 3L)) {
            assertThatThrownBy(() -> service.findPublishedById(hiddenId))
                    .isInstanceOfSatisfying(
                            LearningException.class,
                            error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.NOT_FOUND));
        }
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
}
