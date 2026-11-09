package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyReviewRequest;
import com.sep490.backend.modules.vocabulary.entity.VocabularyNotebookEntry;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewLog;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyReviewRating;
import com.sep490.backend.modules.vocabulary.repository.VocabularyNotebookRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewLogRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewStateRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

class VocabularyReviewServiceTests {
    private final VocabularyNotebookRepository entries = mock(VocabularyNotebookRepository.class);
    private final VocabularyReviewStateRepository states =
            mock(VocabularyReviewStateRepository.class);
    private final VocabularyReviewLogRepository logs = mock(VocabularyReviewLogRepository.class);
    private final Instant now = Instant.parse("2026-11-09T00:00:00Z");
    private final VocabularyReviewService service =
            new VocabularyReviewService(
                    states,
                    logs,
                    entries,
                    mock(VocabularyNotebookService.class),
                    Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void schedulesAllFourRatingsForNewEntries() {
        long[] minutes = {10, 720, 1440, 5760};
        VocabularyReviewRating[] ratings = VocabularyReviewRating.values();
        for (int index = 0; index < ratings.length; index++) {
            VocabularyNotebookEntry entry = new VocabularyNotebookEntry();
            entry.setId((long) index + 1);
            entry.setWord("xin chao");
            when(entries.lockOwned(entry.getId(), 7L)).thenReturn(Optional.of(entry));
            var result =
                    service.rate(
                            7L,
                            entry.getId(),
                            new VocabularyReviewRequest(UUID.randomUUID(), ratings[index]));
            assertThat(result.intervalMinutes()).isEqualTo(minutes[index]);
            assertThat(result.nextReviewAt()).isEqualTo(now.plusSeconds(minutes[index] * 60));
        }
    }

    @Test
    void retryReturnsPreviousResultAndConflictingRatingIsRejected() {
        UUID session = UUID.randomUUID();
        VocabularyNotebookEntry entry = new VocabularyNotebookEntry();
        entry.setId(1L);
        VocabularyReviewLog log = new VocabularyReviewLog();
        log.setVocabularyEntry(entry);
        log.setRating(VocabularyReviewRating.GOOD);
        when(entries.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));
        when(logs.findByVocabularyEntry_IdAndSessionId(1L, session.toString()))
                .thenReturn(Optional.of(log));
        assertThat(
                        service.rate(
                                        7L,
                                        1L,
                                        new VocabularyReviewRequest(
                                                session, VocabularyReviewRating.GOOD))
                                .rating())
                .isEqualTo(VocabularyReviewRating.GOOD);
        assertThatThrownBy(
                        () ->
                                service.rate(
                                        7L,
                                        1L,
                                        new VocabularyReviewRequest(
                                                session, VocabularyReviewRating.EASY)))
                .isInstanceOf(LearningException.class);
        verify(logs, never()).saveAndFlush(any());
        verify(states, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEntriesOwnedByAnotherUser() {
        assertThatThrownBy(
                        () ->
                                service.rate(
                                        8L,
                                        1L,
                                        new VocabularyReviewRequest(
                                                UUID.randomUUID(), VocabularyReviewRating.GOOD)))
                .isInstanceOf(LearningException.class);
        verify(logs, never()).saveAndFlush(any());
        verify(states, never()).saveAndFlush(any());
    }
}
