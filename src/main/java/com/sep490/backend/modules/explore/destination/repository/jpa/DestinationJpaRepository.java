package com.sep490.backend.modules.explore.destination.repository.jpa;

import com.sep490.backend.modules.explore.destination.entity.DestinationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DestinationJpaRepository
        extends JpaRepository<DestinationJpaEntity, Long>,
        JpaSpecificationExecutor<DestinationJpaEntity> {

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
}
