package com.sep490.backend.modules.vocabulary.service;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.course.domain.model.Course;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.lesson.domain.model.Lesson;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonRepository;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.entity.LessonJpaEntity;
import com.sep490.backend.modules.learning.progress.application.AchievementService;
import com.sep490.backend.modules.learning.progress.application.LearnerAccess;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyNotebookRequest;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyNotebookResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewStateResponse;
import com.sep490.backend.modules.vocabulary.entity.VocabularyNotebookEntry;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyMapper;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyNotebookMapper;
import com.sep490.backend.modules.vocabulary.repository.VocabularyNotebookRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewStateRepository;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class VocabularyNotebookService {
    private final VocabularyNotebookRepository vocabulary;
    private final EntityManager em;
    private final VocabularyRepository catalog;
    private final VocabularyReviewStateRepository reviews;
    private final LessonRepository lessons;
    private final CourseRepository courses;
    private final LearnerAccess access;
    private final Clock clock;
    private final UserRepository userRepository;
    private final AchievementService achievementService;

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    public List<VocabularyNotebookResponse> search(
            Long userId, String search, Long courseId, Long lessonId) {
        if (search != null && search.length() > 200) {
            throw new LearningException(
                    LearningException.Kind.INVALID, "Search must be at most 200 characters.");
        }
        String keyword = search == null ? "" : search.trim();
        return vocabulary.search(userId, keyword, courseId, lessonId).stream()
                .map(this::response)
                .toList();
    }

    /** Lay chi tiet theo ID va quyen truy cap hien tai. */
    public VocabularyNotebookResponse get(Long userId, Long id) {
        return response(owned(userId, id));
    }

    /** Them tu vao so ca nhan sau khi kiem tra nguon va quyen truy cap. */
    @Transactional
    public VocabularyNotebookResponse add(Long userId, VocabularyNotebookRequest request) {
        validateLesson(userId, request.lessonId());
        Long masterId = request.masterVocabularyId();
        if (masterId != null) {
            var master =
                    catalog.findById(masterId)
                            .filter(word -> word.getStatus() == VocabularyStatus.PUBLISHED)
                            .map(VocabularyMapper::toResponse)
                            .orElseThrow(() -> LearningException.notFound("Vocabulary"));

            boolean validWord = master.word().equals(request.word().trim());
            boolean validMeaning =
                    master.meanings() != null
                            && master.meanings().stream()
                                    .anyMatch(
                                            meaning ->
                                                    VocabularyText.notebookMeaning(meaning)
                                                            .equals(request.meaning().trim()));
            if (!validWord || !validMeaning) {
                throw new LearningException(
                        LearningException.Kind.INVALID,
                        "Vocabulary content does not match Master Vocabulary.");
            }
            if (vocabulary.findByUser_IdAndMasterVocabulary_Id(userId, masterId).isPresent()) {
                throw new LearningException(
                        LearningException.Kind.CONFLICT, "Vocabulary already saved in notebook.");
            }
        }

        VocabularyNotebookEntry entry = new VocabularyNotebookEntry();
        entry.setUser(em.getReference(User.class, userId));
        entry.setCreatedAt(clock.instant());
        if (masterId != null) {
            entry.setMasterVocabulary(catalog.getReferenceById(masterId));
        }
        updateEntry(entry, request);
        entry = vocabulary.saveAndFlush(entry);

        // Huy hieu duoc kiem tra sau khi tu da duoc luu vao so.
        try {
            int totalWords = vocabulary.countByUser(userId);
            userRepository
                    .findById(userId)
                    .ifPresent(
                            user ->
                                    achievementService.checkAndAwardBadges(
                                            user, "VOCAB_LEARNED", totalWords));
        } catch (Exception exception) {
            log.warn("Không thể kiểm tra huy hiệu từ vựng: {}", exception.getMessage());
        }
        return response(entry);
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @Transactional
    public VocabularyNotebookResponse update(
            Long userId, Long id, VocabularyNotebookRequest request) {
        VocabularyNotebookEntry entry = owned(userId, id);
        Long masterId =
                entry.getMasterVocabulary() == null ? null : entry.getMasterVocabulary().getId();
        Long lessonId = entry.getLesson() == null ? null : entry.getLesson().getId();
        if (request.masterVocabularyId() != null
                && !Objects.equals(request.masterVocabularyId(), masterId)) {
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "The Master Vocabulary link cannot be changed.");
        }
        if (masterId != null
                && (!entry.getWord().equals(request.word().trim())
                        || !entry.getMeaning().equals(request.meaning().trim()))) {
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Saved Master Vocabulary word and meaning cannot be changed.");
        }
        if (!Objects.equals(lessonId, request.lessonId())) {
            validateLesson(userId, request.lessonId());
        }
        updateEntry(entry, request);
        return response(vocabulary.saveAndFlush(entry));
    }

    /** Xoa muc so tu thuoc nguoi dung va lich su on tap lien quan. */
    @Transactional
    public void delete(Long userId, Long id) {
        VocabularyNotebookEntry entry = owned(userId, id);
        vocabulary.deleteOwned(entry.getId(), userId);
    }

    /** Chi lay muc so tu thuoc ve nguoi dung dang thao tac. */
    private VocabularyNotebookEntry owned(Long userId, Long id) {
        return vocabulary
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> LearningException.notFound("Vocabulary entry"));
    }

    /** Gan noi dung request vao muc so tu sau khi da kiem tra nghiep vu. */
    private void updateEntry(VocabularyNotebookEntry entry, VocabularyNotebookRequest request) {
        entry.setLesson(
                request.lessonId() == null
                        ? null
                        : em.getReference(LessonJpaEntity.class, request.lessonId()));
        entry.setWord(request.word().trim());
        entry.setMeaning(request.meaning().trim());
        entry.setPronunciation(trim(request.pronunciation()));
        entry.setExampleSentence(trim(request.exampleSentence()));
        entry.setNote(trim(request.note()));
    }

    /** Kiem tra bai hoc da xuat ban va quyen hoc truoc khi gan vao so tu. */
    private void validateLesson(Long userId, Long lessonId) {
        if (lessonId == null) {
            return;
        }
        Lesson lesson =
                lessons.findById(lessonId)
                        .filter(Lesson::published)
                        .orElseThrow(() -> LearningException.notFound("Lesson"));
        access.requireEnrollment(userId, lesson.courseId(), false);
    }

    /** Chuan hoa khoang trang o dau va cuoi gia tri tuy chon. */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    VocabularyNotebookResponse response(VocabularyNotebookEntry entry) {
        Long lessonId = entry.getLesson() == null ? null : entry.getLesson().getId();
        Lesson lesson = lessonId == null ? null : lessons.findById(lessonId).orElse(null);
        Course course = lesson == null ? null : courses.findById(lesson.courseId()).orElse(null);
        var now = clock.instant();
        VocabularyReviewStateResponse review =
                reviews.findById(entry.getId())
                        .map(
                                state -> {
                                    String memoryStatus = "REVIEW";
                                    if (state.getRepetitionCount() == 0) {
                                        memoryStatus = "LEARNING";
                                    } else if (state.getRepetitionCount() >= 3) {
                                        memoryStatus = "MASTERED";
                                    }
                                    return new VocabularyReviewStateResponse(
                                            memoryStatus,
                                            state.getRepetitionCount(),
                                            state.getIntervalMinutes(),
                                            state.getLastReviewedAt(),
                                            state.getNextReviewAt(),
                                            !state.getNextReviewAt().isAfter(now));
                                })
                        .orElseGet(
                                () ->
                                        new VocabularyReviewStateResponse(
                                                "NEW", 0, 0, null, null, true));

        String audioUrl = null;
        if (entry.getMasterVocabulary() != null) {
            audioUrl =
                    catalog.findById(entry.getMasterVocabulary().getId())
                            .filter(word -> word.getStatus() == VocabularyStatus.PUBLISHED)
                            .map(word -> word.getAudioUrl())
                            .orElse(null);
        }
        return VocabularyNotebookMapper.toResponse(
                entry,
                course == null ? null : course.id(),
                lesson == null ? null : lesson.title(),
                course == null ? null : course.title(),
                audioUrl,
                review);
    }
}
