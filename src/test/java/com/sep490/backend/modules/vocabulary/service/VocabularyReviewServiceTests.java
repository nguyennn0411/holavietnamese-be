package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyReviewRequest;
import com.sep490.backend.modules.vocabulary.entity.VocabularyNotebookEntry;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewLog;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewState;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyNotebookResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewStateResponse;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyReviewRating;
import com.sep490.backend.modules.vocabulary.repository.VocabularyNotebookRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewLogRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewStateRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class VocabularyReviewServiceTests {
    private final VocabularyNotebookRepository entries = mock(VocabularyNotebookRepository.class);
    private final VocabularyReviewStateRepository states =
            mock(VocabularyReviewStateRepository.class);
    private final VocabularyReviewLogRepository logs = mock(VocabularyReviewLogRepository.class);
    private final VocabularyNotebookService notebook = mock(VocabularyNotebookService.class);
    private final Instant now = Instant.parse("2026-11-09T00:00:00Z");
    private final VocabularyReviewService service =
            new VocabularyReviewService(
                    states,
                    logs,
                    entries,
                    notebook,
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

    @Test
    void sessionCreatesOrKeepsIdAndReturnsDueWordsWithPreviousResults() {
        VocabularyNotebookEntry entry = new VocabularyNotebookEntry();
        entry.setId(1L);
        entry.setWord("xin chào");
        var response = new VocabularyNotebookResponse(
                1L, null, null, null, null, "xin chào", "hello", null, null, null,
                now, null, null, new VocabularyReviewStateResponse("NEW", 0, 0, null, null, true));
        VocabularyReviewLog log = new VocabularyReviewLog();
        log.setVocabularyEntry(entry);
        log.setRating(VocabularyReviewRating.GOOD);
        log.setReviewedAt(now);
        log.setNextReviewAt(now.plusSeconds(86_400));
        log.setIntervalMinutes(1_440);
        when(entries.countByUser(7L)).thenReturn(3);
        when(notebook.response(entry)).thenReturn(response);

        UUID existingId = UUID.randomUUID();
        when(entries.findDue(7L, now, existingId.toString())).thenReturn(List.of(entry));
        when(logs.findByVocabularyEntry_User_IdAndSessionIdOrderByReviewedAtAscIdAsc(
                        7L, existingId.toString()))
                .thenReturn(List.of(log));

        var continued = service.session(7L, existingId);
        assertThat(continued.sessionId()).isEqualTo(existingId);
        assertThat(continued.notebookCount()).isEqualTo(3);
        assertThat(continued.entries()).containsExactly(response);
        assertThat(continued.results()).singleElement()
                .satisfies(result -> assertThat(result.rating()).isEqualTo(VocabularyReviewRating.GOOD));

        // A null session ID starts a new session rather than returning null.
        assertThat(service.session(7L, null).sessionId()).isNotNull();
    }

    @Test
    void rejectsReviewBeforeItsDueTimeWithoutWritingStateOrLog() {
        VocabularyNotebookEntry entry = new VocabularyNotebookEntry();
        entry.setId(1L);
        VocabularyReviewState state = new VocabularyReviewState();
        state.setVocabularyEntry(entry);
        state.setNextReviewAt(now.plusSeconds(60));
        when(entries.lockOwned(1L, 7L)).thenReturn(Optional.of(entry));
        when(states.findById(1L)).thenReturn(Optional.of(state));

        assertThatThrownBy(
                        () -> service.rate(
                                7L, 1L,
                                new VocabularyReviewRequest(UUID.randomUUID(), VocabularyReviewRating.GOOD)))
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.CONFLICT));
        verify(states, never()).saveAndFlush(any());
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void scheduleUpdatesCountersEaseBoundsAndOneYearIntervalCap() {
        VocabularyNotebookEntry againEntry = new VocabularyNotebookEntry();
        againEntry.setId(10L);
        againEntry.setWord("again");
        VocabularyReviewState again = state(againEntry, 4, 60_000, "1.30", 2);
        when(entries.lockOwned(10L, 7L)).thenReturn(Optional.of(againEntry));
        when(states.findById(10L)).thenReturn(Optional.of(again));
        service.rate(7L, 10L, new VocabularyReviewRequest(UUID.randomUUID(), VocabularyReviewRating.AGAIN));
        assertThat(again.getRepetitionCount()).isZero();
        assertThat(again.getLapseCount()).isEqualTo(3);
        assertThat(again.getEaseFactor()).isEqualByComparingTo("1.30");
        assertThat(again.getIntervalMinutes()).isEqualTo(10);

        VocabularyNotebookEntry easyEntry = new VocabularyNotebookEntry();
        easyEntry.setId(11L);
        easyEntry.setWord("easy");
        VocabularyReviewState easy = state(easyEntry, 5, 500_000, "3.00", 0);
        when(entries.lockOwned(11L, 7L)).thenReturn(Optional.of(easyEntry));
        when(states.findById(11L)).thenReturn(Optional.of(easy));
        service.rate(7L, 11L, new VocabularyReviewRequest(UUID.randomUUID(), VocabularyReviewRating.EASY));
        assertThat(easy.getRepetitionCount()).isEqualTo(6);
        assertThat(easy.getEaseFactor()).isEqualByComparingTo("3.00");
        assertThat(easy.getIntervalMinutes()).isEqualTo(525_600);
        assertThat(easy.getLastReviewedAt()).isEqualTo(now);
        assertThat(easy.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void hardKeepsRepetitionWhileGoodIncrementsIt() {
        VocabularyNotebookEntry hardEntry = new VocabularyNotebookEntry();
        hardEntry.setId(20L);
        hardEntry.setWord("hard");
        VocabularyReviewState hard = state(hardEntry, 2, 1_000, "2.50", 0);
        when(entries.lockOwned(20L, 7L)).thenReturn(Optional.of(hardEntry));
        when(states.findById(20L)).thenReturn(Optional.of(hard));
        service.rate(7L, 20L, new VocabularyReviewRequest(UUID.randomUUID(), VocabularyReviewRating.HARD));
        assertThat(hard.getRepetitionCount()).isEqualTo(2);
        assertThat(hard.getIntervalMinutes()).isEqualTo(1_200);

        VocabularyNotebookEntry goodEntry = new VocabularyNotebookEntry();
        goodEntry.setId(21L);
        goodEntry.setWord("good");
        VocabularyReviewState good = state(goodEntry, 2, 1_000, "2.50", 0);
        when(entries.lockOwned(21L, 7L)).thenReturn(Optional.of(goodEntry));
        when(states.findById(21L)).thenReturn(Optional.of(good));
        service.rate(7L, 21L, new VocabularyReviewRequest(UUID.randomUUID(), VocabularyReviewRating.GOOD));
        assertThat(good.getRepetitionCount()).isEqualTo(3);
        assertThat(good.getIntervalMinutes()).isEqualTo(2_500);
    }

    private static VocabularyReviewState state(
            VocabularyNotebookEntry entry, int repetitions, long minutes, String ease, int lapses) {
        var state = new VocabularyReviewState();
        state.setVocabularyEntry(entry);
        state.setRepetitionCount(repetitions);
        state.setIntervalMinutes(minutes);
        state.setEaseFactor(new BigDecimal(ease));
        state.setLapseCount(lapses);
        state.setNextReviewAt(Instant.parse("2026-11-09T00:00:00Z"));
        return state;
    }
}
