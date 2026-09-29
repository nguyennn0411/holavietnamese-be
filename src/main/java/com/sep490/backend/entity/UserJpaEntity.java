package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class UserJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false, length = 20) private String role = "LEARNER";
}
