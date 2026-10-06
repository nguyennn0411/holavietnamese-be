package com.sep490.backend.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class UserJpaEntity {
    private Long id;

    private String email;

    private String passwordHash;

    private boolean enabled = true;

    private String role = "LEARNER";
}
