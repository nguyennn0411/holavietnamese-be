package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.PassportEntryJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface PassportEntryJpaRepository extends JpaRepository<PassportEntryJpaEntity, Long> {
    @EntityGraph(attributePaths = "destination")
    List<PassportEntryJpaEntity> findByUserIdOrderByStampedAtAscIdAsc(Long userId);
    Optional<PassportEntryJpaEntity> findByUserIdAndDestinationId(Long userId, Long destinationId);
}
