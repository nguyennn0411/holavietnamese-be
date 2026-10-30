package com.sep490.backend.modules.learning.course.infrastructure.persistence;
import com.sep490.backend.modules.learning.course.domain.CourseStatus;
import com.sep490.backend.modules.learning.course.domain.model.Course;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseJpaRepository;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseMapper;


import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository 
@RequiredArgsConstructor
public class CourseRepository {
    private final CourseJpaRepository repository;
    public List<Course> findAvailable() { return repository.findByStatusOrderByIdAsc(CourseStatus.PUBLISHED).stream().map(CourseMapper::toDomain).toList(); }
    public Optional<Course> findById(Long id) { return repository.findById(id).map(CourseMapper::toDomain); }
    public int countPublishedLessons(Long id) { return repository.countPublishedLessons(id); }
    public long count() { return repository.count(); }
}
