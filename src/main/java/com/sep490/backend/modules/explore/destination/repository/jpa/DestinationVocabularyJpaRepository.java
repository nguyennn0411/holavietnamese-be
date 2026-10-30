package com.sep490.backend.modules.explore.destination.repository.jpa;

import com.sep490.backend.modules.explore.destination.entity.DestinationVocabularyJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface DestinationVocabularyJpaRepository extends JpaRepository<DestinationVocabularyJpaEntity, Long> {
    @EntityGraph(attributePaths = "vocabulary")
    List<DestinationVocabularyJpaEntity> findByDestinationIdOrderByDisplayOrderAscIdAsc(Long destinationId);
}
