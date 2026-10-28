package com.sep490.backend.modules.admin.cms.repository;

import com.sep490.backend.modules.admin.cms.entity.SiteBanner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteBannerRepository extends JpaRepository<SiteBanner, Long> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByFeatureKey(String featureKey);
}
