package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.DestinationLessonJpaEntity;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.*;

public interface DestinationLessonJpaRepository extends JpaRepository<DestinationLessonJpaEntity, Long> {
    @EntityGraph(attributePaths = {"lesson", "lesson.course"})
    List<DestinationLessonJpaEntity> findByDestinationIdOrderByDisplayOrderAscIdAsc(Long destinationId);
    @EntityGraph(attributePaths = {"lesson", "lesson.course"})
    List<DestinationLessonJpaEntity> findByDestinationIdInOrderByDisplayOrderAscIdAsc(Collection<Long> destinationIds);
}
