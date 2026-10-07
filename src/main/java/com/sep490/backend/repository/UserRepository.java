package com.sep490.backend.repository;

import com.sep490.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles " +
           "WHERE (u.username = :usernameOrEmail OR u.email = :usernameOrEmail) AND u.isRemoved = false")
    Optional<User> findActiveByUsernameWithRoles(@Param("usernameOrEmail") String usernameOrEmail);

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles " +
           "WHERE u.email = :email AND u.isRemoved = false")
    Optional<User> findActiveByEmailWithRoles(@Param("email") String email);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
