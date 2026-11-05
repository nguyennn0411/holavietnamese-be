package com.sep490.backend.modules.explore.regional.repository.jpa;

import com.sep490.backend.modules.explore.regional.entity.RegionalVocabularyJpaEntity;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface RegionalVocabularyJpaRepository extends JpaRepository<RegionalVocabularyJpaEntity, Long>,
        JpaSpecificationExecutor<RegionalVocabularyJpaEntity> {
    List<RegionalVocabularyJpaEntity> findByVocabularyIdAndRegion(Long vocabularyId, VietnamRegion region);
}
