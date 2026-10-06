package com.sep490.backend.service;

import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sep490.backend.service.VocabularyService;
import com.sep490.backend.repository.*;
import com.sep490.backend.dto.request.VocabularyRequest;
import com.sep490.backend.dto.response.VocabularyResponse;
import com.sep490.backend.dto.model.*;
import com.sep490.backend.exception.LearningException;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class VocabularyService {
    private final VocabularyRepository vocabulary;
    private final LessonRepository lessons;
    private final CourseRepository courses;
    private final LearnerAccess access;
    private final Clock clock;
    public List<VocabularyResponse> search(Long userId, String search, Long courseId, Long lessonId) {
        if (search != null && search.length() > 200) throw new LearningException(LearningException.Kind.INVALID, "Search must be at most 200 characters.");
        return vocabulary.search(userId, search == null ? "" : search.trim(), courseId, lessonId).stream().map(this::response).toList();
    }
    public VocabularyResponse get(Long userId, Long id) { return response(owned(userId, id)); }
    @Transactional
    public VocabularyResponse add(Long userId, VocabularyRequest r) {
        validateLesson(userId, r.lessonId());
        return response(vocabulary.save(new VocabularyEntry(null, userId, r.lessonId(), r.word().trim(), r.meaning().trim(), trim(r.pronunciation()), trim(r.exampleSentence()), trim(r.note()), clock.instant())));
    }
    @Transactional
    public VocabularyResponse update(Long userId, Long id, VocabularyRequest r) {
        var e = owned(userId, id);
        if (!Objects.equals(e.lessonId(), r.lessonId())) validateLesson(userId, r.lessonId());
        return response(vocabulary.save(new VocabularyEntry(e.id(), userId, r.lessonId(), r.word().trim(), r.meaning().trim(), trim(r.pronunciation()), trim(r.exampleSentence()), trim(r.note()), e.createdAt())));
    }
    @Transactional
    public void delete(Long userId, Long id) { vocabulary.delete(owned(userId, id)); }
    private VocabularyEntry owned(Long userId, Long id) { return vocabulary.findOwned(id, userId).orElseThrow(() -> LearningException.notFound("Vocabulary entry")); }
    private void validateLesson(Long userId, Long lessonId) {
        if (lessonId == null) return;
        var l = lessons.findById(lessonId).filter(Lesson::published).orElseThrow(() -> LearningException.notFound("Lesson"));
        access.requireEnrollment(userId, l.courseId(), false);
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private VocabularyResponse response(VocabularyEntry e) {
        Lesson l = e.lessonId() == null ? null : lessons.findById(e.lessonId()).orElse(null);
        Course c = l == null ? null : courses.findById(l.courseId()).orElse(null);
        return new VocabularyResponse(e.id(), e.lessonId(), c == null ? null : c.id(), l == null ? null : l.title(), c == null ? null : c.title(), e.word(), e.meaning(), e.pronunciation(), e.exampleSentence(), e.note(), e.createdAt());
    }
}
