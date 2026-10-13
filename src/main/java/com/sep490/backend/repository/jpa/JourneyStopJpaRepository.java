package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.JourneyStopJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface JourneyStopJpaRepository extends JpaRepository<JourneyStopJpaEntity, Long> {
    @EntityGraph(attributePaths = "destination")
    List<JourneyStopJpaEntity> findAllByOrderByDisplayOrderAscIdAsc();
}
