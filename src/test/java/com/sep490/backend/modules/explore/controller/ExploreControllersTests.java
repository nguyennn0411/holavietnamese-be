package com.sep490.backend.modules.explore.controller;

import static org.mockito.Mockito.*;

import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.service.DestinationExploreService;
import com.sep490.backend.modules.explore.service.DestinationService;
import com.sep490.backend.modules.explore.service.RegionalVocabularyService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for learner-facing Explore controller delegation. */
class ExploreControllersTests {
    @Test
    void destinationControllerDelegatesPublicSearchAndDetail() {
        var service = mock(DestinationService.class);
        var controller = new DestinationController(service);

        controller.search("Hà", VietnamRegion.NORTH);
        controller.findById(2L);

        verify(service).searchPublished("Hà", VietnamRegion.NORTH);
        verify(service).findPublishedById(2L);
    }

    @Test
    void regionalControllerDelegatesComparisonQueries() {
        var service = mock(RegionalVocabularyService.class);
        var controller = new RegionalVocabularyController(service);

        controller.list("dứa", VietnamRegion.SOUTH);
        controller.get(4L);

        verify(service).comparisons("dứa", VietnamRegion.SOUTH);
        verify(service).comparison(4L);
    }

    @Test
    void destinationContentControllerSeparatesPublicAndManagementViews() {
        var service = mock(DestinationExploreService.class);
        var controller = new DestinationExploreController(service);
        var request = new DestinationExploreContentRequest(List.of(1L), List.of("culture"), List.of("scenario"));

        controller.content(3L);
        controller.management(3L);
        controller.save(3L, request);

        verify(service).content(3L, false);
        verify(service).content(3L, true);
        verify(service).save(3L, request);
    }
}
