package com.sep490.backend.modules.vocabulary.service;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyReviewRequest;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewResultResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewSessionResponse;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewLog;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewState;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyReviewRating;
import com.sep490.backend.modules.vocabulary.repository.VocabularyNotebookRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewLogRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewStateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyReviewService {
    private final VocabularyReviewStateRepository states;
    private final VocabularyReviewLogRepository logs;
    private final VocabularyNotebookRepository entries;
    private final VocabularyNotebookService vocabulary;
    private final Clock clock;

    /** Tao hoac tiep tuc phien on tap voi cac tu den han. */
    public VocabularyReviewSessionResponse session(Long userId, UUID sessionId) {
        UUID id = sessionId == null ? UUID.randomUUID() : sessionId;
        return new VocabularyReviewSessionResponse(
                id,
                entries.countByUser(userId),
                entries.findDue(userId, clock.instant(), id.toString()).stream()
                        .map(vocabulary::response)
                        .toList(),
                logs
                        .findByVocabularyEntry_User_IdAndSessionIdOrderByReviewedAtAscIdAsc(
                                userId, id.toString())
                        .stream()
                        .map(this::response)
                        .toList());
    }

    /** Luu danh gia on tap, xu ly request gui lai va tinh lich on tiep theo. */
    @Transactional
    public VocabularyReviewResultResponse rate(
            Long userId, Long entryId, VocabularyReviewRequest request) {
        // Khoa muc so tu ke ca lan on dau tien chua co trang thai.
        var entry =
                entries.lockOwned(entryId, userId)
                        .orElseThrow(() -> LearningException.notFound("Vocabulary entry"));
        var existing =
                logs.findByVocabularyEntry_IdAndSessionId(entryId, request.sessionId().toString());
        if (existing.isPresent()) {
            if (existing.get().getRating() != request.rating()) {
                throw new LearningException(
                        LearningException.Kind.CONFLICT,
                        "This word was already rated in this session.");
            }
            return response(
                    existing.get()); // Gui lai ket qua cu de tranh doi lich hoac ghi log hai lan.
        }
        var now = clock.instant();
        var state =
                states.findById(entryId)
                        .orElseGet(
                                () -> {
                                    var initial = new VocabularyReviewState();
                                    initial.setVocabularyEntry(entry);
                                    initial.setNextReviewAt(now);
                                    return initial;
                                });
        if (state.getNextReviewAt().isAfter(now)) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "This word is not due for review yet.");
        }
        schedule(state, request.rating());
        state.setLastReviewedAt(now);
        state.setNextReviewAt(now.plus(state.getIntervalMinutes(), ChronoUnit.MINUTES));
        state.setUpdatedAt(now);

        var log = new VocabularyReviewLog();
        log.setVocabularyEntry(entry);
        log.setSessionId(request.sessionId().toString());
        log.setRating(request.rating());
        log.setReviewedAt(now);
        log.setNextReviewAt(state.getNextReviewAt());
        log.setIntervalMinutes(state.getIntervalMinutes());
        states.saveAndFlush(state);
        logs.saveAndFlush(log);
        return response(log);
    }

    // Tu moi duoc hen on sau 10 phut, 12 gio, 1 ngay hoac 4 ngay.
    // Cac lan sau nhan khoang cach cu va gioi han toi da mot nam.
    private static void schedule(VocabularyReviewState state, VocabularyReviewRating rating) {
        long previous = state.getIntervalMinutes();
        double ease = state.getEaseFactor().doubleValue();
        long interval =
                switch (rating) {
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
        double adjustment =
                switch (rating) {
                    case AGAIN -> -0.2;
                    case HARD -> -0.15;
                    case GOOD -> 0;
                    case EASY -> 0.15;
                };
        state.setEaseFactor(
                BigDecimal.valueOf(Math.max(1.3, Math.min(3, ease + adjustment)))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** Chuyen du lieu da xu ly sang response tra ve nguoi dung. */
    private VocabularyReviewResultResponse response(VocabularyReviewLog log) {
        return new VocabularyReviewResultResponse(
                log.getVocabularyEntry().getId(),
                log.getVocabularyEntry().getWord(),
                log.getRating(),
                log.getReviewedAt(),
                log.getNextReviewAt(),
                log.getIntervalMinutes());
    }
}
