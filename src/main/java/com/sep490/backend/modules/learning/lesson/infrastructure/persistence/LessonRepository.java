package com.sep490.backend.modules.learning.lesson.infrastructure.persistence;
import com.sep490.backend.modules.learning.lesson.domain.model.Lesson;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonJpaRepository;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonMapper;


import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository 
@RequiredArgsConstructor
public class LessonRepository {
    private final LessonJpaRepository repository;
    public Optional<Lesson> findById(Long id) { return repository.findById(id).map(LessonMapper::toDomain); }
    public List<Lesson> findPublishedByCourse(Long id) { return repository.findByCourseIdAndPublishedTrueOrderByLessonOrderAscIdAsc(id).stream().map(LessonMapper::toDomain).toList(); }
    public long count() { return repository.count(); }
}
