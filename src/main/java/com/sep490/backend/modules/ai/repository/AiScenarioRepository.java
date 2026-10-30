package com.sep490.backend.modules.ai.repository;

import com.sep490.backend.modules.ai.entity.AiScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiScenarioRepository extends JpaRepository<AiScenario, String> {
    List<AiScenario> findByStatusOrderByCreatedAtDesc(String status);
    List<AiScenario> findAllByOrderByCreatedAtDesc();
}
