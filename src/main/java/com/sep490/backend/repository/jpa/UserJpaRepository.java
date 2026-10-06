package com.sep490.backend.repository.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sep490.backend.entity.UserJpaEntity;
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Integer> { Optional<UserJpaEntity> findByEmail(String email); }
