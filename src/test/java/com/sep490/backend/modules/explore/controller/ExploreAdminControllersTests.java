package com.sep490.backend.modules.explore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.modules.explore.controller.admin.AdminDestinationController;
import com.sep490.backend.modules.explore.controller.admin.AdminRegionalVocabularyController;
import com.sep490.backend.modules.explore.dto.request.DestinationManagementRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.service.AdminDestinationService;
import com.sep490.backend.modules.explore.service.AdminRegionalVocabularyService;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import org.junit.jupiter.api.Test;
import java.util.List;

/** Exercises all thin admin Explore endpoint adapters and both review decision branches. */
class ExploreAdminControllersTests {
    @Test
    void destinationAdminControllerDelegatesCrudManagementAndWorkflow() {
        var service = mock(AdminDestinationService.class);
        var controller = new AdminDestinationController(service);
        var request = destination("hue");
        var management = new DestinationManagementRequest(
                request, new DestinationExploreContentRequest(List.of(), List.of(), List.of()));

        controller.search("Huế", DestinationStatus.DRAFT, VietnamRegion.CENTRAL, 1, 10);
        controller.findById(5L);
        controller.create(request);
        controller.update(5L, request);
        controller.updateManagement(5L, management);
        controller.submitReview(5L);
        controller.archive(5L);
        controller.restore(5L);
        controller.revise(5L);

        verify(service).searchPage("Huế", DestinationStatus.DRAFT, VietnamRegion.CENTRAL, 1, 10);
        verify(service).findById(5L);
        verify(service).createDraft(request);
        verify(service).updateDraft(5L, request);
        verify(service).updateManagement(5L, management);
        verify(service).submitReview(5L);
        verify(service).archive(5L);
        verify(service).restore(5L);
        verify(service).revise(5L);
    }

    @Test
    void destinationReviewUsesDecisionSpecificMessages() {
        var service = mock(AdminDestinationService.class);
        var controller = new AdminDestinationController(service);

        var approve = controller.review(
                1L, new ContentReviewRequest(ContentReviewRequest.Decision.APPROVE));
        var reject = controller.review(
                2L, new ContentReviewRequest(ContentReviewRequest.Decision.REJECT));

        assertThat(approve.getMessage()).contains("approved");
        assertThat(reject.getMessage()).contains("draft");
        verify(service).review(1L, ContentReviewRequest.Decision.APPROVE);
        verify(service).review(2L, ContentReviewRequest.Decision.REJECT);
    }

    @Test
    void regionalAdminControllerDelegatesEveryOperation() {
        var service = mock(AdminRegionalVocabularyService.class);
        var controller = new AdminRegionalVocabularyController(service);
        var request = new RegionalVocabularyRequest(
                9L, VietnamRegion.SOUTH, "thơm", "dứa", null, null, null,
                null, null, null, null, VocabularyStatus.DRAFT);

        controller.list("thơm", VietnamRegion.SOUTH, 9L, VocabularyStatus.DRAFT, 2, 30);
        controller.get(6L);
        controller.create(request);
        controller.update(6L, request);
        controller.submitReview(6L);
        controller.archive(6L);
        controller.restore(6L);
        controller.revise(6L);
        controller.review(6L, new ContentReviewRequest(ContentReviewRequest.Decision.APPROVE));

        verify(service).searchPage("thơm", VietnamRegion.SOUTH, 9L, VocabularyStatus.DRAFT, 2, 30);
        verify(service).get(6L);
        verify(service).createDraft(request);
        verify(service).updateDraft(6L, request);
        verify(service).submitReview(6L);
        verify(service).archive(6L);
        verify(service).restore(6L);
        verify(service).revise(6L);
        verify(service).review(6L, ContentReviewRequest.Decision.APPROVE);
    }

    @Test
    void regionalBulkApprovalReportsZeroAndPositiveCounts() {
        var service = mock(AdminRegionalVocabularyService.class);
        var controller = new AdminRegionalVocabularyController(service);
        when(service.approveAllPending()).thenReturn(0, 3);

        assertThat(controller.approveAllPending().getMessage()).contains("No regional");
        assertThat(controller.approveAllPending().getMessage()).contains("3 regional");
    }

    private static DestinationRequest destination(String slug) {
        return new DestinationRequest(
                "Huế", "Hue", slug, VietnamRegion.CENTRAL,
                null, null, null, null, null, DestinationStatus.DRAFT);
    }
}
