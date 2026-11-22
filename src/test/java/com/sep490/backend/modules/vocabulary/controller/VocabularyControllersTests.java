package com.sep490.backend.modules.vocabulary.controller;

import static org.mockito.Mockito.*;

import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyNotebookRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyReviewRequest;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyReviewRating;
import com.sep490.backend.modules.vocabulary.service.VocabularyNotebookService;
import com.sep490.backend.modules.vocabulary.service.VocabularyReviewService;
import com.sep490.backend.modules.vocabulary.service.VocabularyService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the thin learner controllers so parameter or endpoint wiring regressions are visible. */
class VocabularyControllersTests {
    private final LearnerPrincipal learner =
            new LearnerPrincipal(21L, "learner@test.local", "", true, "LEARNER");

    @Test
    void catalogControllerDelegatesListPageDetailAndLinks() {
        var service = mock(VocabularyService.class);
        var controller = new VocabularyController(service);

        controller.page("xin", 1, 25, true);
        controller.search("chào");
        controller.findById(8L);
        controller.links(8L);

        verify(service).searchPage("xin", 1, 25, true);
        verify(service).searchPublished("chào", null);
        verify(service).findPublishedById(8L);
        verify(service).links(8L);
    }

    @Test
    void notebookControllerAlwaysUsesAuthenticatedLearnerId() {
        var service = mock(VocabularyNotebookService.class);
        var controller = new VocabularyNotebookController(service);
        var request = new VocabularyNotebookRequest(null, "xin", "hello", null, null, null);

        controller.list(learner, "xin", 5L, 6L);
        controller.page(learner, "xin", 5L, 6L, 1, 20);
        controller.practice(learner, 20);
        controller.findByMasterVocabulary(learner, 10L);
        controller.get(learner, 7L);
        controller.add(learner, request);
        controller.update(learner, 7L, request);
        controller.delete(learner, 7L);

        verify(service).search(21L, "xin", 5L, 6L);
        verify(service).page(21L, "xin", 5L, 6L, 1, 20);
        verify(service).practice(21L, 20);
        verify(service).findByMasterVocabulary(21L, 10L);
        verify(service).get(21L, 7L);
        verify(service).add(21L, request);
        verify(service).update(21L, 7L, request);
        verify(service).delete(21L, 7L);
    }

    @Test
    void reviewControllerForwardsSessionAndRatingWithLearnerId() {
        var service = mock(VocabularyReviewService.class);
        var controller = new VocabularyReviewController(service);
        var sessionId = UUID.randomUUID();
        var request = new VocabularyReviewRequest(sessionId, VocabularyReviewRating.GOOD);

        controller.session(learner, sessionId);
        controller.rate(learner, 12L, request);

        verify(service).session(21L, sessionId);
        verify(service).rate(21L, 12L, request);
    }
}
