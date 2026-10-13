package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.JourneyProgressJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyProgressJpaRepository extends JpaRepository<JourneyProgressJpaEntity, Long> {
    List<JourneyProgressJpaEntity> findByUserId(Long userId);
    Optional<JourneyProgressJpaEntity> findByUserIdAndDestinationId(Long userId, Long destinationId);
}
