package com.sep490.backend.modules.auth.repository;

import com.sep490.backend.modules.auth.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDescIdDesc(Long userId, Boolean isRead, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Optional<Notification> findByUserIdAndEventKey(Long userId, String eventKey);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            SELECT n.id FROM Notification n
            WHERE n.isRead = true AND n.createdAt < :cutoff
            ORDER BY n.createdAt, n.id
            """)
    List<Long> findExpiredReadIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.id IN :ids")
    int deleteAllByIdInBatch(@Param("ids") Collection<Long> ids);
}
