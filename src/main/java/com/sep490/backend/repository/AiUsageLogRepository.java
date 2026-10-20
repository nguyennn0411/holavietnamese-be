package com.sep490.backend.repository;

import com.sep490.backend.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, String> {
    List<AiUsageLog> findTop50ByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(u) FROM AiUsageLog u WHERE u.createdAt >= :since")
    Long countRequestsSince(LocalDateTime since);

    @Query("SELECT COALESCE(SUM(u.tokensIn + u.tokensOut), 0) FROM AiUsageLog u WHERE u.createdAt >= :since")
    Long sumTokensSince(LocalDateTime since);

    @Query("SELECT COALESCE(AVG(u.latencyMs), 0) FROM AiUsageLog u WHERE u.createdAt >= :since")
    Double avgLatencySince(LocalDateTime since);

    @Query("SELECT COALESCE(SUM(u.costUsd), 0) FROM AiUsageLog u WHERE u.createdAt >= :since")
    BigDecimal sumCostSince(LocalDateTime since);

    @Query("SELECT u.mode, COUNT(u) FROM AiUsageLog u WHERE u.createdAt >= :since GROUP BY u.mode")
    List<Object[]> countByModeGrouped(LocalDateTime since);

    @Query("SELECT u.mode, COUNT(u), COALESCE(SUM(u.tokensIn + u.tokensOut), 0), COALESCE(SUM(u.costUsd), 0) "
            + "FROM AiUsageLog u WHERE u.createdAt >= :since GROUP BY u.mode")
    List<Object[]> summarizeByModeSince(LocalDateTime since);

    @Query("SELECT COUNT(u) FROM AiUsageLog u WHERE u.createdAt >= :since AND u.statusCode >= 400")
    Long countErrorsSince(LocalDateTime since);
}
