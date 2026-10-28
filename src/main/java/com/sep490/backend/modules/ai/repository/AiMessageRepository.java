package com.sep490.backend.modules.ai.repository;

import com.sep490.backend.modules.ai.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, String> {
    List<AiMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    long countBySessionId(String sessionId);
}
