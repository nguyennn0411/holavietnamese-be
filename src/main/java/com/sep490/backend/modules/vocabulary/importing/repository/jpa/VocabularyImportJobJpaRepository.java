package com.sep490.backend.modules.vocabulary.importing.repository.jpa;

import com.sep490.backend.modules.vocabulary.importing.entity.VocabularyImportJobJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface VocabularyImportJobJpaRepository extends JpaRepository<VocabularyImportJobJpaEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from VocabularyImportJobJpaEntity j where j.id = :id")
    Optional<VocabularyImportJobJpaEntity> lockById(String id);
}
