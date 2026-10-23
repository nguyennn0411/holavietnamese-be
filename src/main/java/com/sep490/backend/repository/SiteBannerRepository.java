package com.sep490.backend.repository;

import com.sep490.backend.entity.SiteBanner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteBannerRepository extends JpaRepository<SiteBanner, Long> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByFeatureKey(String featureKey);
}
