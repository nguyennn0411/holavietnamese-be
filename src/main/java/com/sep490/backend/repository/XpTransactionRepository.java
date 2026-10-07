package com.sep490.backend.repository;

import com.sep490.backend.entity.XpTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface XpTransactionRepository extends JpaRepository<XpTransaction, Long> {
    List<XpTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(x.amount), 0) FROM XpTransaction x WHERE x.user.id = :userId AND x.eventType = :eventType AND x.createdAt >= :since")
    int sumAmountByUserIdAndEventTypeSince(@Param("userId") Long userId, @Param("eventType") String eventType, @Param("since") LocalDateTime since);
}
