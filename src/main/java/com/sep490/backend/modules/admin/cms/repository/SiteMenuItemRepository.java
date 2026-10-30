package com.sep490.backend.modules.admin.cms.repository;

import com.sep490.backend.modules.admin.cms.entity.SiteMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteMenuItemRepository extends JpaRepository<SiteMenuItem, Long> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByFeatureKey(String featureKey);
}
