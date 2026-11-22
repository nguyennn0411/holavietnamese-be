package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.dto.request.*;
import com.sep490.backend.modules.vocabulary.entity.enums.*;
import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class VocabularyCompatibilityTests extends ContentApiTestSupport {
    @Autowired AdminVocabularyService admin;
    @Autowired VocabularyService catalog;
    @Autowired VocabularyNotebookService notebook;
    @Autowired VocabularyReviewService review;

    private VocabularyRequest word(String name, VocabularyStatus status) {
        return new VocabularyRequest(
                name,
                null,
                "noun",
                null,
                status,
                List.of(new VocabularyMeaningRequest("hello", "greeting", null, 0, List.of())));
    }

    @Test
    void masterNotebookLinksSurviveNoteUpdatesAndReviewSessionsResume() {
        String email = UUID.randomUUID() + "@notebook.test";
        jdbc.update(
                "INSERT INTO users(username,email,password_hash) VALUES(?,?,'test')", email, email);
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        var master = admin.create(word("master-" + owner, VocabularyStatus.PUBLISHED));
        var input =
                new VocabularyNotebookRequest(
                        null, master.word(), "hello", null, null, "old", master.id());
        var saved = notebook.add(owner, input);
        assertThatThrownBy(() -> notebook.add(owner, input)).isInstanceOf(LearningException.class);
        var updated =
                notebook.update(
                        owner,
                        saved.id(),
                        new VocabularyNotebookRequest(
                                null, master.word(), "hello", null, null, "new", null));
        assertThat(updated.masterVocabularyId()).isEqualTo(master.id());
        assertThat(updated.note()).isEqualTo("new");
        assertThatThrownBy(
                        () ->
                                notebook.update(
                                        owner,
                                        saved.id(),
                                        new VocabularyNotebookRequest(
                                                null, "changed", "hello", null, null, "new", null)))
                .isInstanceOf(LearningException.class);

        var session = review.session(owner, null);
        assertThat(session.entries()).extracting(entry -> entry.id()).contains(saved.id());
        review.rate(
                owner,
                saved.id(),
                new VocabularyReviewRequest(session.sessionId(), VocabularyReviewRating.GOOD));
        var resumed = review.session(owner, session.sessionId());
        assertThat(resumed.entries()).isEmpty();
        assertThat(resumed.reviewedEntries()).extracting(entry -> entry.id()).containsExactly(saved.id());
        assertThat(resumed.results()).hasSize(1);
        assertThatThrownBy(
                        () ->
                                review.rate(
                                        owner,
                                        saved.id(),
                                        new VocabularyReviewRequest(
                                                UUID.randomUUID(), VocabularyReviewRating.GOOD)))
                .isInstanceOf(LearningException.class);
    }

    @Test
    void fiveWordSessionResumesWithPendingCardsAndCompleteReviewedFlashcards() {
        String email = UUID.randomUUID() + "@review-session.test";
        jdbc.update(
                "INSERT INTO users(username,email,password_hash) VALUES(?,?,'test')", email, email);
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);

        var saved =
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(
                                index ->
                                        notebook.add(
                                                owner,
                                                new VocabularyNotebookRequest(
                                                        null,
                                                        "word-" + index,
                                                        "meaning-" + index,
                                                        null,
                                                        null,
                                                        null,
                                                        null)))
                        .toList();
        var session = review.session(owner, null);
        assertThat(session.entries()).hasSize(5);

        var ratings =
                List.of(
                        VocabularyReviewRating.AGAIN,
                        VocabularyReviewRating.HARD,
                        VocabularyReviewRating.GOOD);
        for (int index = 0; index < ratings.size(); index++) {
            review.rate(
                    owner,
                    saved.get(index).id(),
                    new VocabularyReviewRequest(session.sessionId(), ratings.get(index)));
        }

        var resumed = review.session(owner, session.sessionId());
        assertThat(resumed.entries())
                .extracting(entry -> entry.id())
                .containsExactly(saved.get(3).id(), saved.get(4).id());
        assertThat(resumed.reviewedEntries())
                .extracting(entry -> entry.id())
                .containsExactly(saved.get(0).id(), saved.get(1).id(), saved.get(2).id());
        assertThat(resumed.results())
                .extracting(result -> result.rating())
                .containsExactlyElementsOf(ratings);
        assertThat(resumed.results())
                .extracting(result -> result.intervalMinutes())
                .containsExactly(10L, 60L, 1_440L);

        review.rate(
                owner,
                saved.get(3).id(),
                new VocabularyReviewRequest(session.sessionId(), VocabularyReviewRating.GOOD));
        review.rate(
                owner,
                saved.get(4).id(),
                new VocabularyReviewRequest(session.sessionId(), VocabularyReviewRating.EASY));
        assertThat(review.session(owner, session.sessionId()).entries()).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM vocabulary_review_logs log
                                JOIN vocabulary_entries entry
                                  ON entry.id = log.vocabulary_entry_id
                                WHERE entry.user_id=? AND log.session_id=?
                                """,
                                Integer.class,
                                owner,
                                session.sessionId().toString()))
                .isEqualTo(5);
    }

    @Test
    void bulkReviewOnlyPublishesPendingWordsAndEnforcesReviewerPermission() throws Exception {
        var pending = admin.createDraft(word("bulk-pending", VocabularyStatus.DRAFT));
        var draft = admin.createDraft(word("bulk-draft", VocabularyStatus.DRAFT));
        admin.submitReview(pending.id());
        var content = new LearnerPrincipal(9002L, "content@test.local", "", true, "CONTENT");
        var reviewer = new LearnerPrincipal(9003L, "reviewer@test.local", "", true, "ADMIN");
        mvc.perform(
                        post("/api/admin/vocabulary/review/approve-all")
                                .with(user(content))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/admin/vocabulary/review/approve-all")
                                .with(user(reviewer))
                                .with(csrf()))
                .andExpect(status().isOk());
        assertThat(catalog.findPublishedById(pending.id()).status())
                .isEqualTo(VocabularyStatus.PUBLISHED);
        assertThatThrownBy(() -> catalog.findPublishedById(draft.id()))
                .isInstanceOf(LearningException.class);
        assertThat(admin.approveAllPending()).isZero();
    }

    @Test
    void dictionaryKeepsBilingualSearchExactMatchingAndBoundedPages() {
        var published = admin.create(word("compat-xin-chao", VocabularyStatus.PUBLISHED));
        admin.create(word("compat-xin-chao-hidden", VocabularyStatus.DRAFT));
        assertThat(catalog.searchPublished("greeting", "noun"))
                .extracting(item -> item.id())
                .contains(published.id());
        var page = catalog.searchPage("compat-xin-chao", 0, 500, true);
        assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.getContent()).extracting(item -> item.id()).containsExactly(published.id());
        assertThat(admin.search("compat-xin-chao", VocabularyStatus.DRAFT, "noun")).hasSize(1);
    }
}
