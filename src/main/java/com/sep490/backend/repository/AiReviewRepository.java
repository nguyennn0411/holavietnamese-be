package com.sep490.backend.repository;

import com.sep490.backend.entity.AiReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiReviewRepository extends JpaRepository<AiReview, String> {
    List<AiReview> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AiReview> findAllByOrderByCreatedAtDesc();
    List<AiReview> findByStatusOrderByCreatedAtDesc(String status);
}
