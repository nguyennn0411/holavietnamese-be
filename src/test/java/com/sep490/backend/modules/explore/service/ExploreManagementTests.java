package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.*;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.request.*;
import com.sep490.backend.modules.explore.entity.enums.*;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

class ExploreManagementTests extends ContentApiTestSupport {
    @Autowired AdminDestinationService destinations;
    @Autowired AdminRegionalVocabularyService regional;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidContentRollsBackDestinationChanges() {
        String slug = "rollback-" + UUID.randomUUID();
        var original =
                new DestinationRequest(
                        "Ten cu",
                        "Old name",
                        slug,
                        VietnamRegion.NORTH,
                        null,
                        null,
                        null,
                        null,
                        null,
                        DestinationStatus.DRAFT);
        var saved = destinations.createDraft(original);
        try {
            var changed =
                    new DestinationRequest(
                            "Ten moi",
                            "New name",
                            slug,
                            VietnamRegion.NORTH,
                            null,
                            null,
                            null,
                            null,
                            null,
                            DestinationStatus.DRAFT);
            var invalidContent =
                    new DestinationExploreContentRequest(
                            List.of(Long.MAX_VALUE), List.of(), List.of());
            assertThatThrownBy(
                            () ->
                                    destinations.updateManagement(
                                            saved.id(),
                                            new DestinationManagementRequest(
                                                    changed, invalidContent)))
                    .isInstanceOf(LearningException.class);
            assertThat(destinations.findById(saved.id()).nameVi()).isEqualTo("Ten cu");
        } finally {
            jdbc.update("DELETE FROM destinations WHERE id=?", saved.id());
        }
    }

    @Test
    void regionalWorkflowRejectsDuplicatesAndRequiresReview() {
        jdbc.update(
                "INSERT INTO vocabularies (word, search_word, part_of_speech, status, created_at,"
                        + " updated_at) VALUES ('regional-workflow', 'regional-workflow', 'noun',"
                        + " 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long master =
                jdbc.queryForObject(
                        "SELECT id FROM vocabularies WHERE word='regional-workflow'", Long.class);
        var input =
                new RegionalVocabularyRequest(
                        master,
                        VietnamRegion.SOUTH,
                        "bien the",
                        "nghia",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        VocabularyStatus.DRAFT);
        var saved = regional.createDraft(input);
        assertThatThrownBy(() -> regional.createDraft(input)).isInstanceOf(LearningException.class);
        assertThatThrownBy(() -> regional.review(saved.id(), ContentReviewRequest.Decision.APPROVE))
                .isInstanceOf(LearningException.class);
        regional.submitReview(saved.id());
        assertThat(regional.review(saved.id(), ContentReviewRequest.Decision.APPROVE).status())
                .isEqualTo(VocabularyStatus.PUBLISHED);
        regional.archive(saved.id());
        assertThat(regional.restore(saved.id()).status()).isEqualTo(VocabularyStatus.DRAFT);
    }
}
