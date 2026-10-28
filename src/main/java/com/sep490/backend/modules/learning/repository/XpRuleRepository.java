package com.sep490.backend.modules.learning.repository;

import com.sep490.backend.modules.learning.entity.XpRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface XpRuleRepository extends JpaRepository<XpRule, Long> {
    Optional<XpRule> findByEventType(String eventType);
}
