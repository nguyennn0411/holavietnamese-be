package com.sep490.backend.modules.vocabulary.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.modules.vocabulary.controller.admin.AdminVocabularyController;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyRequest;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests that verify every admin vocabulary endpoint delegates the correct command. */
class AdminVocabularyControllerTests {
    private AdminVocabularyService service;
    private AdminVocabularyController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminVocabularyService.class);
        controller = new AdminVocabularyController(service);
    }

    @Test
    void readEndpointsForwardEveryFilterAndIdentifier() {
        controller.page("xin", VocabularyStatus.DRAFT, "noun", 2, 15);
        controller.search("chào", VocabularyStatus.PUBLISHED, "phrase");
        controller.findById(9L);

        verify(service).searchPage("xin", VocabularyStatus.DRAFT, "noun", 2, 15, false);
        verify(service).search("chào", VocabularyStatus.PUBLISHED, "phrase");
        verify(service).findById(9L);
    }

    @Test
    void writeAndWorkflowEndpointsDelegateToMatchingServiceMethods() {
        var request = new VocabularyRequest(
                "xin", null, "noun", null, VocabularyStatus.DRAFT,
                List.of(new VocabularyMeaningRequest("hello", null, null, 0, List.of())));

        assertThat(controller.create(request).getMessage()).contains("created");
        assertThat(controller.update(7L, request).getMessage()).contains("updated");
        assertThat(controller.submitReview(7L).getMessage()).contains("submitted");
        controller.archive(7L);
        controller.restore(7L);
        assertThat(controller.revise(7L).getMessage()).contains("draft");

        verify(service).createDraft(request);
        verify(service).updateDraft(7L, request);
        verify(service).submitReview(7L);
        verify(service).archive(7L);
        verify(service).restore(7L);
        verify(service).revise(7L);
    }

    @Test
    void reviewAndBulkApprovalReturnDecisionSpecificMessages() {
        var approved = controller.review(
                3L, new ContentReviewRequest(ContentReviewRequest.Decision.APPROVE));
        var rejected = controller.review(
                4L, new ContentReviewRequest(ContentReviewRequest.Decision.REJECT));
        when(service.approveAllPending()).thenReturn(0, 2);

        var none = controller.approveAllPending();
        var two = controller.approveAllPending();

        assertThat(approved.getMessage()).contains("approved");
        assertThat(rejected.getMessage()).contains("draft");
        assertThat(none.getMessage()).contains("No vocabulary");
        assertThat(two.getMessage()).contains("2 vocabulary");
        verify(service).review(3L, ContentReviewRequest.Decision.APPROVE);
        verify(service).review(4L, ContentReviewRequest.Decision.REJECT);
    }
}
