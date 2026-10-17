package com.sep490.backend.repository;

import com.sep490.backend.dto.request.*;
import com.sep490.backend.entity.*;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.learning.shared.ContentStore;
import com.sep490.backend.repository.jpa.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class JourneyRepository {
    private final JourneyStopJpaRepository stops;
    private final JourneyProgressJpaRepository progress;
    private final PassportEntryJpaRepository passport;
    private final DestinationJpaRepository destinations;
    private final DestinationLessonJpaRepository lessonLinks;
    private final DestinationVocabularyJpaRepository vocabularyLinks;
    private final LessonJpaRepository lessons;
    private final VocabularyWordJpaRepository words;
    private final ContentStore content;
    private final EntityManager em;

    public List<JourneyStopJpaEntity> stops() { return stops.findAllByOrderByDisplayOrderAscIdAsc(); }
    public List<JourneyProgressJpaEntity> progress(long user) { return progress.findByUserId(user); }
    public List<PassportEntryJpaEntity> passport(long user) { return passport.findByUserIdOrderByStampedAtAscIdAsc(user); }
    public DestinationJpaEntity destination(long id) {
        return destinations.findById(id).orElseThrow(() -> LearningException.notFound("Destination"));
    }
    public List<DestinationLessonJpaEntity> lessons(long id) {
        return lessonLinks.findByDestinationIdOrderByDisplayOrderAscIdAsc(id);
    }
    public List<DestinationVocabularyJpaEntity> words(long id) {
        return vocabularyLinks.findByDestinationIdOrderByDisplayOrderAscIdAsc(id);
    }
    public List<DestinationLessonJpaEntity> lessonLinks(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : lessonLinks.findByDestinationIdInOrderByDisplayOrderAscIdAsc(ids);
    }
    public void lockUser(long user) { content.one("SELECT id FROM users WHERE id=? FOR UPDATE", user); }
    public DestinationJpaEntity lockDestination(long id) {
        var destination = em.find(DestinationJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (destination == null) throw LearningException.notFound("Destination");
        return destination;
    }
    public Optional<JourneyProgressJpaEntity> progress(long user, long destination) { return progress.findByUserIdAndDestinationId(user, destination); }
    public Optional<PassportEntryJpaEntity> passport(long user, long destination) { return passport.findByUserIdAndDestinationId(user, destination); }
    public JourneyProgressJpaEntity save(JourneyProgressJpaEntity row) { return progress.saveAndFlush(row); }
    public PassportEntryJpaEntity save(PassportEntryJpaEntity row) { return passport.saveAndFlush(row); }
    public Set<Long> completedLessons(long user) {
        return content.rows("SELECT DISTINCT p.lesson_id FROM lesson_progress p JOIN enrollments e ON e.id=p.enrollment_id WHERE e.user_id=? AND p.status='COMPLETED' AND p.completed_at IS NOT NULL", user)
                .stream().map(r -> ContentStore.id(r, "lessonId")).collect(Collectors.toSet());
    }

    public void configure(JourneyRequest request) {
        // One configuration transaction, including an initially empty journey.
        // Lock existing destination rows in a consistent order before changing the stops.
        var available = em.createQuery("select d from DestinationJpaEntity d order by d.id", DestinationJpaEntity.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().stream()
                .collect(Collectors.toMap(DestinationJpaEntity::getId, d -> d));
        unique(request.stops().stream().map(JourneyRequest.Stop::destinationId).toList());
        for (var stop : request.stops()) if (!available.containsKey(stop.destinationId()))
            throw LearningException.notFound("Destination");
        var old = stops().stream().collect(Collectors.toMap(s -> s.getDestination().getId(), s -> s));
        var selected = request.stops().stream().map(JourneyRequest.Stop::destinationId).collect(Collectors.toSet());
        stops.deleteAll(old.values().stream().filter(s -> !selected.contains(s.getDestination().getId())).toList());
        for (int i = 0; i < request.stops().size(); i++) {
            var input = request.stops().get(i);
            var row = old.getOrDefault(input.destinationId(), new JourneyStopJpaEntity());
            row.setDestination(available.get(input.destinationId()));
            row.setDisplayOrder(i + 1);
            row.setStatus(input.status());
            stops.save(row);
        }
        // Removing a stop never removes user progress or passport history.
        stops.flush();
    }

    public void link(long id, DestinationContentRequest request) {
        lockDestination(id);
        unique(request.lessonIds()); unique(request.vocabularyIds());
        var selectedLessons = lessons.findAllById(request.lessonIds()).stream().collect(Collectors.toMap(LessonJpaEntity::getId, l -> l));
        var selectedWords = words.findAllById(request.vocabularyIds()).stream().collect(Collectors.toMap(VocabularyWordJpaEntity::getId, w -> w));
        if (selectedLessons.size() != request.lessonIds().size()) throw LearningException.notFound("Lesson");
        if (selectedWords.size() != request.vocabularyIds().size()) throw LearningException.notFound("Vocabulary");
        var oldLessons = lessons(id).stream().collect(Collectors.toMap(l -> l.getLesson().getId(), l -> l));
        var oldWords = words(id).stream().collect(Collectors.toMap(w -> w.getVocabulary().getId(), w -> w));
        lessonLinks.deleteAll(oldLessons.values().stream().filter(l -> !selectedLessons.containsKey(l.getLesson().getId())).toList());
        vocabularyLinks.deleteAll(oldWords.values().stream().filter(w -> !selectedWords.containsKey(w.getVocabulary().getId())).toList());
        for (int i = 0; i < request.lessonIds().size(); i++) {
            long lesson = request.lessonIds().get(i);
            var row = oldLessons.getOrDefault(lesson, new DestinationLessonJpaEntity());
            row.setDestinationId(id); row.setLesson(selectedLessons.get(lesson)); row.setDisplayOrder(i + 1);
            lessonLinks.save(row);
        }
        for (int i = 0; i < request.vocabularyIds().size(); i++) {
            long word = request.vocabularyIds().get(i);
            var row = oldWords.getOrDefault(word, new DestinationVocabularyJpaEntity());
            row.setDestinationId(id); row.setVocabulary(selectedWords.get(word)); row.setDisplayOrder(i + 1);
            vocabularyLinks.save(row);
        }
        lessonLinks.flush(); vocabularyLinks.flush();
    }
    private static void unique(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw new LearningException(LearningException.Kind.INVALID, "Duplicate content or destination IDs.");
    }
}
