package com.sep490.backend.repository;

import com.sep490.backend.entity.SiteFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteFeatureFlagRepository extends JpaRepository<SiteFeatureFlag, String> {
}
