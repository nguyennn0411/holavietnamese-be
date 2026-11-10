package com.sep490.backend.modules.vocabulary.importing.repository;

import com.sep490.backend.modules.vocabulary.importing.entity.VocabularyImportJob;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface VocabularyImportJobRepository extends JpaRepository<VocabularyImportJob, String> {
    /** Khoa phien import de tranh xac nhan dong thoi. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from VocabularyImportJob j where j.id = :id")
    Optional<VocabularyImportJob> lockById(String id);
}
