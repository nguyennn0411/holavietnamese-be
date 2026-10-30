package com.sep490.backend.modules.culture.repository;

import com.sep490.backend.modules.culture.entity.CultureArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CultureArticleRepository extends JpaRepository<CultureArticle, String>, JpaSpecificationExecutor<CultureArticle> {

    List<CultureArticle> findByStatusOrderByPublishedAtDesc(String status);

    List<CultureArticle> findByStatusAndIsFeaturedTrueOrderByPublishedAtDesc(String status);

    List<CultureArticle> findByStatusAndDestinationIdOrderByPublishedAtDesc(String status, String destinationId);

    List<CultureArticle> findByStatusAndCategory_IdOrderByPublishedAtDesc(String status, String categoryId);

    @Modifying
    @Query("UPDATE CultureArticle a SET a.views = a.views + 1 WHERE a.id = :id")
    void incrementViews(@Param("id") String id);
}
