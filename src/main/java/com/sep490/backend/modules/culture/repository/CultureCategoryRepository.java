package com.sep490.backend.modules.culture.repository;

import com.sep490.backend.modules.culture.entity.CultureCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CultureCategoryRepository extends JpaRepository<CultureCategory, String> {
    List<CultureCategory> findAllByOrderByDisplayOrderAsc();
    Optional<CultureCategory> findBySlug(String slug);
}
