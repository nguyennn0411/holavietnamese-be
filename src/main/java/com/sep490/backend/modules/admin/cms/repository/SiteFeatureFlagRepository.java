package com.sep490.backend.modules.admin.cms.repository;

import com.sep490.backend.modules.admin.cms.entity.SiteFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteFeatureFlagRepository extends JpaRepository<SiteFeatureFlag, String> {
}
