package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.lesson.domain.model.Lesson;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonRepository;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.entity.LessonJpaEntity;
import com.sep490.backend.modules.learning.progress.application.AchievementService;
import com.sep490.backend.modules.learning.progress.application.LearnerAccess;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyNotebookRequest;
import com.sep490.backend.modules.vocabulary.entity.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.entity.VocabularyNotebookEntry;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.repository.VocabularyNotebookRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;
import com.sep490.backend.modules.vocabulary.repository.VocabularyReviewStateRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for notebook ownership, master links, lesson access and badge isolation. */
class VocabularyNotebookServiceTests {
    private VocabularyNotebookRepository entries;
    private EntityManager em;
    private VocabularyRepository catalog;
    private VocabularyReviewStateRepository reviews;
    private LessonRepository lessons;
    private LearnerAccess access;
    private UserRepository users;
    private AchievementService achievements;
    private VocabularyNotebookService service;
    private final Instant now = Instant.parse("2026-11-10T00:00:00Z");

    @BeforeEach
    void setUp() {
        entries = mock(VocabularyNotebookRepository.class);
        em = mock(EntityManager.class);
        catalog = mock(VocabularyRepository.class);
        reviews = mock(VocabularyReviewStateRepository.class);
        lessons = mock(LessonRepository.class);
        access = mock(LearnerAccess.class);
        users = mock(UserRepository.class);
        achievements = mock(AchievementService.class);
        service =
                new VocabularyNotebookService(
                        entries, em, catalog, reviews, lessons, mock(CourseRepository.class),
                        access, Clock.fixed(now, ZoneOffset.UTC), users, achievements);

        when(entries.saveAndFlush(any(VocabularyNotebookEntry.class)))
                .thenAnswer(
                        invocation -> {
                            VocabularyNotebookEntry saved = invocation.getArgument(0);
                            if (saved.getId() == null) saved.setId(100L);
                            return saved;
                        });
    }

    @Test
    void searchValidatesLengthTrimsKeywordAndMapsNewReviewState() {
        assertKind(
                () -> service.search(7L, "x".repeat(201), null, null),
                LearningException.Kind.INVALID);

        var entry = entry(1L, 7L, null, null);
        when(entries.search(7L, "xin", null, null)).thenReturn(List.of(entry));
        when(reviews.findById(1L)).thenReturn(Optional.empty());

        var result = service.search(7L, "  xin  ", null, null);

        verify(entries).search(7L, "xin", null, null);
        assertThat(result).singleElement()
                .satisfies(response -> assertThat(response.review().memoryStatus()).isEqualTo("NEW"));
    }

    @Test
    void addManualEntryTrimsFieldsAndAwardsBadgeAfterSaving() {
        var user = user(7L);
        when(em.getReference(User.class, 7L)).thenReturn(user);
        when(entries.countByUser(7L)).thenReturn(12);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        var response = service.add(7L, request(null, "  xin chào  ", "  hello  ", null));

        assertThat(response.word()).isEqualTo("xin chào");
        assertThat(response.meaning()).isEqualTo("hello");
        assertThat(response.pronunciation()).isEqualTo("/sin chow/");
        assertThat(response.createdAt()).isEqualTo(now);
        verify(achievements).checkAndAwardBadges(user, "VOCAB_LEARNED", 12);
    }

    @Test
    void addMasterEntryRequiresPublishedMatchingAndUniqueMaster() {
        when(catalog.findById(10L)).thenReturn(Optional.empty());
        assertKind(
                () -> service.add(7L, request(null, "xin chào", "hello", 10L)),
                LearningException.Kind.NOT_FOUND);

        when(catalog.findById(10L))
                .thenReturn(Optional.of(master(10L, "xin chào", "hello", VocabularyStatus.DRAFT)));
        assertKind(
                () -> service.add(7L, request(null, "xin chào", "hello", 10L)),
                LearningException.Kind.NOT_FOUND);

        var published = master(10L, "xin chào", "hello", VocabularyStatus.PUBLISHED);
        when(catalog.findById(10L)).thenReturn(Optional.of(published));
        assertKind(
                () -> service.add(7L, request(null, "different", "hello", 10L)),
                LearningException.Kind.INVALID);
        assertKind(
                () -> service.add(7L, request(null, "xin chào", "different", 10L)),
                LearningException.Kind.INVALID);

        when(entries.findByUser_IdAndMasterVocabulary_Id(7L, 10L))
                .thenReturn(Optional.of(entry(1L, 7L, published, null)));
        assertKind(
                () -> service.add(7L, request(null, "xin chào", "hello", 10L)),
                LearningException.Kind.CONFLICT);
    }

    @Test
    void updateCannotChangeMasterLinkWordOrMeaning() {
        var master = master(10L, "xin chào", "hello", VocabularyStatus.PUBLISHED);
        var existing = entry(1L, 7L, master, null);
        when(entries.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(existing));

        assertKind(
                () -> service.update(7L, 1L, request(null, "xin chào", "hello", 11L)),
                LearningException.Kind.INVALID);
        assertKind(
                () -> service.update(7L, 1L, request(null, "changed", "hello", 10L)),
                LearningException.Kind.INVALID);
        assertKind(
                () -> service.update(7L, 1L, request(null, "xin chào", "changed", 10L)),
                LearningException.Kind.INVALID);
    }

    @Test
    void assigningLessonRequiresPublishedLessonAndEnrollment() {
        when(em.getReference(User.class, 7L)).thenReturn(user(7L));
        when(lessons.findById(99L)).thenReturn(Optional.empty());
        assertKind(
                () -> service.add(7L, request(99L, "word", "meaning", null)),
                LearningException.Kind.NOT_FOUND);

        var unpublished = new Lesson(99L, 5L, "Lesson", null, 1, null, null, null, 10, false);
        when(lessons.findById(99L)).thenReturn(Optional.of(unpublished));
        assertKind(
                () -> service.add(7L, request(99L, "word", "meaning", null)),
                LearningException.Kind.NOT_FOUND);

        var published = new Lesson(99L, 5L, "Lesson", null, 1, null, null, null, 10, true);
        var lessonEntity = new LessonJpaEntity();
        lessonEntity.setId(99L);
        when(lessons.findById(99L)).thenReturn(Optional.of(published));
        when(em.getReference(LessonJpaEntity.class, 99L)).thenReturn(lessonEntity);

        service.add(7L, request(99L, "word", "meaning", null));

        verify(access).requireEnrollment(7L, 5L, false);
    }

    @Test
    void badgeFailureDoesNotRollbackSavedNotebookEntry() {
        var user = user(7L);
        when(em.getReference(User.class, 7L)).thenReturn(user);
        when(entries.countByUser(7L)).thenThrow(new IllegalStateException("badge unavailable"));

        // Badge evaluation is deliberately best-effort; the vocabulary save remains successful.
        assertThat(service.add(7L, request(null, "word", "meaning", null)).id()).isEqualTo(100L);
        verify(entries).saveAndFlush(any(VocabularyNotebookEntry.class));
    }

    @Test
    void ownershipProtectsGetUpdateAndDelete() {
        when(entries.findByIdAndUserId(1L, 8L)).thenReturn(Optional.empty());
        assertKind(() -> service.get(8L, 1L), LearningException.Kind.NOT_FOUND);
        assertKind(
                () -> service.update(8L, 1L, request(null, "word", "meaning", null)),
                LearningException.Kind.NOT_FOUND);
        assertKind(() -> service.delete(8L, 1L), LearningException.Kind.NOT_FOUND);
    }

    private static VocabularyNotebookRequest request(
            Long lessonId, String word, String meaning, Long masterId) {
        return new VocabularyNotebookRequest(
                lessonId, word, meaning, " /sin chow/ ", " example ", " note ", masterId);
    }

    private static VocabularyWord master(
            long id, String text, String translation, VocabularyStatus status) {
        var word = new VocabularyWord();
        word.setId(id);
        word.setWord(text);
        word.setPartOfSpeech("phrase");
        word.setStatus(status);
        var meaning = new VocabularyMeaning();
        meaning.setVocabulary(word);
        meaning.setTranslationEn(translation);
        word.getMeanings().add(meaning);
        return word;
    }

    private static VocabularyNotebookEntry entry(
            long id, long userId, VocabularyWord master, LessonJpaEntity lesson) {
        var entry = new VocabularyNotebookEntry();
        entry.setId(id);
        entry.setUser(user(userId));
        entry.setMasterVocabulary(master);
        entry.setLesson(lesson);
        entry.setWord(master == null ? "word" : master.getWord());
        entry.setMeaning(master == null ? "meaning" : "hello");
        entry.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return entry;
    }

    private static User user(long id) {
        var user = new User();
        user.setId(id);
        return user;
    }

    private static void assertKind(Runnable action, LearningException.Kind expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(expected));
    }
}
