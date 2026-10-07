package com.sep490.backend.repository.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sep490.backend.entity.User;
public interface UserJpaRepository extends JpaRepository<User, Long> { Optional<User> findByEmail(String email); }
