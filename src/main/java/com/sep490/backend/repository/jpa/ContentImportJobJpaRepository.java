package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.ContentImportJobJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ContentImportJobJpaRepository extends JpaRepository<ContentImportJobJpaEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from ContentImportJobJpaEntity j where j.id = :id")
    Optional<ContentImportJobJpaEntity> lockById(String id);
}
