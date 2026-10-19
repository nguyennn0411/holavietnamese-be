package com.sep490.backend.repository;

import com.sep490.backend.entity.AiSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSettingRepository extends JpaRepository<AiSetting, Long> {
}
