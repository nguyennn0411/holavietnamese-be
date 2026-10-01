package com.sep490.backend.repository;

import com.sep490.backend.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserId(Integer userId);
    Optional<UserBadge> findByUserIdAndBadgeId(Integer userId, Long badgeId);
    boolean existsByUserIdAndBadgeCode(Integer userId, String badgeCode);
}
