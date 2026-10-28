package com.sep490.backend.modules.learning.repository;

import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.modules.learning.repository.jpa.CourseJpaRepository;
import com.sep490.backend.modules.learning.mapper.CourseMapper;
import com.sep490.backend.modules.learning.dto.model.Course;
import com.sep490.backend.modules.learning.entity.enums.CourseStatus;

@Repository 
@RequiredArgsConstructor
public class CourseRepository {
    private final CourseJpaRepository repository;
    public List<Course> findAvailable() { return repository.findByStatusOrderByIdAsc(CourseStatus.PUBLISHED).stream().map(CourseMapper::toDomain).toList(); }
    public Optional<Course> findById(Long id) { return repository.findById(id).map(CourseMapper::toDomain); }
    public int countPublishedLessons(Long id) { return repository.countPublishedLessons(id); }
    public long count() { return repository.count(); }
}
