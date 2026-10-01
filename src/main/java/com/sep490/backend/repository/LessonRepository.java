package com.sep490.backend.repository;

import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.repository.LessonRepository;
import com.sep490.backend.repository.jpa.LessonJpaRepository;
import com.sep490.backend.mapper.LessonMapper;
import com.sep490.backend.dto.model.Lesson;
@Repository @RequiredArgsConstructor
public class LessonRepository {
    private final LessonJpaRepository repository;
    public Optional<Lesson> findById(Long id) { return repository.findById(id).map(LessonMapper::toDomain); }
    public List<Lesson> findPublishedByCourse(Long id) { return repository.findByCourseIdAndPublishedTrueOrderByLessonOrderAscIdAsc(id).stream().map(LessonMapper::toDomain).toList(); }
}
