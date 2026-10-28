package com.sep490.backend.repository;

import com.sep490.backend.entity.AiSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiSessionRepository extends JpaRepository<AiSession, String> {
    List<AiSession> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AiSession> findByUserIdAndModeOrderByCreatedAtDesc(Long userId, String mode);
    Optional<AiSession> findByIdAndUserId(String id, Long userId);
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}
