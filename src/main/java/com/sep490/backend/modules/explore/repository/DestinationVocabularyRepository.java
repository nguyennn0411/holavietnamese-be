package com.sep490.backend.modules.explore.repository;

import com.sep490.backend.modules.explore.entity.DestinationVocabulary;

import org.springframework.data.jpa.repository.*;

import java.util.List;

public interface DestinationVocabularyRepository
        extends JpaRepository<DestinationVocabulary, Long> {
    /** Lay lien ket tu vung cua dia diem theo thu tu hien thi va ID. */
    @EntityGraph(attributePaths = "vocabulary")
    List<DestinationVocabulary> findByDestinationIdOrderByDisplayOrderAscIdAsc(Long destinationId);
}
