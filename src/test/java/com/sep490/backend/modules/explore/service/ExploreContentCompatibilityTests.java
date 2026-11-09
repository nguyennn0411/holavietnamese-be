package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.dto.response.DestinationExploreContentResponse;
import com.sep490.backend.modules.explore.entity.enums.*;
import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class ExploreContentCompatibilityTests extends ContentApiTestSupport {
    @Autowired AdminDestinationService destinations;
    @Autowired DestinationExploreService content;

    private long word(String status) {
        String word = "linked-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO vocabularies(word,part_of_speech,status) VALUES(?,'noun',?)",
                word,
                status);
        return jdbc.queryForObject("SELECT id FROM vocabularies WHERE word=?", Long.class, word);
    }

    private String scenario(String status) {
        String id = "linked-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ai_scenarios(id,title,ai_role_name,ai_role_initial_greeting,status)"
                        + " VALUES(?,'Title','Role','Hello',?)",
                id,
                status);
        return id;
    }

    private String culture() {
        String id = "linked-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO culture_articles(id,title,status,is_featured,views)"
                        + " VALUES(?,'Title','PUBLISHED',FALSE,0)",
                id);
        return id;
    }

    @Test
    void linksKeepRequestOrderAndPublicReadsFilterUnavailableContentWithoutEditingSources() {
        var destination =
                destinations.create(
                        new DestinationRequest(
                                "Dia diem",
                                "Destination",
                                "linked-" + UUID.randomUUID(),
                                VietnamRegion.NORTH,
                                null,
                                null,
                                null,
                                null,
                                null,
                                DestinationStatus.PUBLISHED));
        long first = word("PUBLISHED");
        long hidden = word("DRAFT");
        long last = word("PUBLISHED");
        String active = scenario("ACTIVE");
        String inactive = scenario("INACTIVE");
        var cultureIds = List.of(culture(), culture());
        var sourceWords = jdbc.queryForList("SELECT * FROM vocabularies ORDER BY id");
        var sourceScenarios = jdbc.queryForList("SELECT * FROM ai_scenarios ORDER BY id");
        var sourceCultures = jdbc.queryForList("SELECT * FROM culture_articles ORDER BY id");
        var learning = jdbc.queryForList("SELECT * FROM enrollments ORDER BY id");

        var saved =
                content.save(
                        destination.id(),
                        new DestinationExploreContentRequest(
                                List.of(last, hidden, first),
                                cultureIds,
                                List.of(inactive, active)));
        assertThat(saved.vocabulary())
                .extracting(DestinationExploreContentResponse.Word::id)
                .containsExactly(last, hidden, first);
        var visible = content.content(destination.id(), false);
        assertThat(visible.vocabulary())
                .extracting(DestinationExploreContentResponse.Word::id)
                .containsExactly(last, first);
        assertThat(visible.scenarios())
                .extracting(DestinationExploreContentResponse.Scenario::id)
                .containsExactly(active);
        assertThat(visible.cultureIds()).containsExactlyElementsOf(cultureIds);
        assertThat(jdbc.queryForList("SELECT * FROM vocabularies ORDER BY id"))
                .isEqualTo(sourceWords);
        assertThat(jdbc.queryForList("SELECT * FROM ai_scenarios ORDER BY id"))
                .usingRecursiveComparison()
                .isEqualTo(sourceScenarios);
        assertThat(jdbc.queryForList("SELECT * FROM culture_articles ORDER BY id"))
                .usingRecursiveComparison()
                .isEqualTo(sourceCultures);
        assertThat(jdbc.queryForList("SELECT * FROM enrollments ORDER BY id")).isEqualTo(learning);
    }

    @Test
    void invalidOrDuplicateReferencesPreserveThePreviousLinks() {
        var destination =
                destinations.createDraft(
                        new DestinationRequest(
                                "Ban nhap",
                                "Draft",
                                "invalid-" + UUID.randomUUID(),
                                VietnamRegion.SOUTH,
                                null,
                                null,
                                null,
                                null,
                                null,
                                DestinationStatus.DRAFT));
        long existing = word("PUBLISHED");
        content.save(
                destination.id(),
                new DestinationExploreContentRequest(List.of(existing), List.of(), List.of()));
        assertThatThrownBy(
                        () ->
                                content.save(
                                        destination.id(),
                                        new DestinationExploreContentRequest(
                                                List.of(Long.MAX_VALUE), List.of(), List.of())))
                .isInstanceOf(LearningException.class);
        assertThatThrownBy(
                        () ->
                                content.save(
                                        destination.id(),
                                        new DestinationExploreContentRequest(
                                                List.of(existing, existing), List.of(), List.of())))
                .isInstanceOf(LearningException.class);
        assertThat(content.content(destination.id(), true).vocabulary())
                .extracting(DestinationExploreContentResponse.Word::id)
                .containsExactly(existing);
    }
}
