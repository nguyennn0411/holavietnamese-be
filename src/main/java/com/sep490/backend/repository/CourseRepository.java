package com.sep490.backend.repository;

import java.util.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.repository.CourseRepository;
import com.sep490.backend.repository.jpa.CourseJpaRepository;
import com.sep490.backend.mapper.CourseMapper;
import com.sep490.backend.dto.model.Course;
import com.sep490.backend.entity.enums.CourseStatus;
@Repository @RequiredArgsConstructor
public class CourseRepository {
    private final CourseJpaRepository repository;
    public List<Course> findAvailable() { return repository.findByStatusOrderByIdAsc(CourseStatus.PUBLISHED).stream().map(CourseMapper::toDomain).toList(); }
    public Optional<Course> findById(Long id) { return repository.findById(id).map(CourseMapper::toDomain); }
    public int countPublishedLessons(Long id) { return repository.countPublishedLessons(id); }
}
