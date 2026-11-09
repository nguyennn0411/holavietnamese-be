package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyNotebookRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyReviewRequest;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyReviewRating;
import com.sep490.backend.support.ContentApiTestSupport;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

class VocabularyNotebookFlowTests extends ContentApiTestSupport {
    @Autowired EntityManager em;
    @Autowired VocabularyNotebookService notebook;
    @Autowired VocabularyReviewService review;

    @Test
    void savesReviewsAndDeletesEntryWithoutExposingItToAnotherOwner() {
        User owner = new User();
        owner.setUsername("notebook-refactor@test.local");
        owner.setEmail("notebook-refactor@test.local");
        owner.setPasswordHash("test-hash");
        em.persist(owner);
        em.flush();
        var request =
                new VocabularyNotebookRequest(
                        null, "xin chao", "hello", null, null, "ghi chu", null);
        var entry = notebook.add(owner.getId(), request);
        assertThat(notebook.get(owner.getId(), entry.id()).note()).isEqualTo("ghi chu");
        assertThatThrownBy(() -> notebook.get(owner.getId() + 100000, entry.id()))
                .isInstanceOf(LearningException.class);
        var session = UUID.randomUUID();
        var result =
                review.rate(
                        owner.getId(),
                        entry.id(),
                        new VocabularyReviewRequest(session, VocabularyReviewRating.GOOD));
        assertThat(result.intervalMinutes()).isEqualTo(1440);
        assertThat(
                        review.rate(
                                owner.getId(),
                                entry.id(),
                                new VocabularyReviewRequest(session, VocabularyReviewRating.GOOD)))
                .isEqualTo(result);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabulary_review_logs WHERE"
                                        + " vocabulary_entry_id=?",
                                Integer.class,
                                entry.id()))
                .isEqualTo(1);
        notebook.delete(owner.getId(), entry.id());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabulary_review_logs WHERE"
                                        + " vocabulary_entry_id=?",
                                Integer.class,
                                entry.id()))
                .isZero();
    }
}
