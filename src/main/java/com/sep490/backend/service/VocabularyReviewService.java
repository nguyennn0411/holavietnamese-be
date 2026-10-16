package com.sep490.backend.service;

import com.sep490.backend.dto.request.VocabularyReviewRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.entity.*;
import com.sep490.backend.entity.enums.VocabularyReviewRating;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class VocabularyReviewService {
    private final VocabularyReviewRepository reviews;
    private final VocabularyRepository entries;
    private final VocabularyService vocabulary;
    private final Clock clock;

    public VocabularyReviewSessionResponse session(Long userId, UUID sessionId) {
        UUID id = sessionId == null ? UUID.randomUUID() : sessionId;
        return new VocabularyReviewSessionResponse(id, entries.countByUser(userId),
                reviews.due(userId, clock.instant(), id.toString()).stream().map(vocabulary::response).toList(),
                reviews.results(userId, id.toString()).stream().map(this::response).toList());
    }

    @Transactional
    public VocabularyReviewResultResponse rate(Long userId, Long entryId, VocabularyReviewRequest request) {
        // Lock the notebook row, including the first review when there is no state row yet.
        var entry = reviews.lockOwned(entryId, userId)
                .orElseThrow(() -> LearningException.notFound("Vocabulary entry"));
        var existing = reviews.result(entryId, request.sessionId().toString());
        if (existing.isPresent()) {
            if (existing.get().getRating() != request.rating()) {
                throw new LearningException(LearningException.Kind.CONFLICT, "This word was already rated in this session.");
            }
            return response(existing.get()); // A retried request must not reschedule or log twice.
        }
        var now = clock.instant();
        var state = reviews.state(entryId).orElseGet(() -> {
            var initial = new VocabularyReviewStateJpaEntity();
            initial.setVocabularyEntry(entry);
            initial.setNextReviewAt(now);
            return initial;
        });
        if (state.getNextReviewAt().isAfter(now)) {
            throw new LearningException(LearningException.Kind.CONFLICT, "This word is not due for review yet.");
        }
        schedule(state, request.rating());
        state.setLastReviewedAt(now);
        state.setNextReviewAt(now.plus(state.getIntervalMinutes(), ChronoUnit.MINUTES));
        state.setUpdatedAt(now);

        var log = new VocabularyReviewLogJpaEntity();
        log.setVocabularyEntry(entry);
        log.setSessionId(request.sessionId().toString());
        log.setRating(request.rating());
        log.setReviewedAt(now);
        log.setNextReviewAt(state.getNextReviewAt());
        log.setIntervalMinutes(state.getIntervalMinutes());
        reviews.save(state, log);
        return response(log);
    }

    // Minimal spaced repetition: 10m / 12h / 1d / 4d for a new word. Later
    // successful reviews multiply the previous interval; cap it at one year.
    private static void schedule(VocabularyReviewStateJpaEntity state, VocabularyReviewRating rating) {
        long previous = state.getIntervalMinutes();
        double ease = state.getEaseFactor().doubleValue();
        long interval = switch (rating) {
            case AGAIN -> 10;
            case HARD -> Math.max(720, Math.round(previous * 1.2));
            case GOOD -> Math.max(1440, Math.round(previous * ease));
            case EASY -> Math.max(5760, Math.round(previous * ease * 1.3));
        };
        state.setIntervalMinutes(Math.min(interval, 525600));
        if (rating == VocabularyReviewRating.AGAIN) {
            state.setRepetitionCount(0);
            state.setLapseCount(state.getLapseCount() + 1);
        } else if (rating != VocabularyReviewRating.HARD) {
            state.setRepetitionCount(state.getRepetitionCount() + 1);
        }
        double adjustment = switch (rating) { case AGAIN -> -0.2; case HARD -> -0.15; case GOOD -> 0; case EASY -> 0.15; };
        state.setEaseFactor(BigDecimal.valueOf(Math.max(1.3, Math.min(3, ease + adjustment)))
                .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private VocabularyReviewResultResponse response(VocabularyReviewLogJpaEntity log) {
        return new VocabularyReviewResultResponse(log.getVocabularyEntry().getId(), log.getVocabularyEntry().getWord(),
                log.getRating(), log.getReviewedAt(), log.getNextReviewAt(), log.getIntervalMinutes());
    }
}
