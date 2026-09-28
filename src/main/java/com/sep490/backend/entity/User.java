package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email", length = 100, unique = true, nullable = false)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Ngôn ngữ mẹ đẻ của người học (vd: "en", "ko", "ja", "zh", "fr")
     */
    @Column(name = "native_language", length = 50)
    private String nativeLanguage;

    /**
     * Mục tiêu học tiếng Việt (vd: "travel", "business", "exam_vsl", "daily_communication")
     */
    @Column(name = "learning_goal", length = 255)
    private String learningGoal;

    /**
     * Trình độ mục tiêu theo khung năng lực tiếng Việt (vd: "A1", "A2", "B1", "B2", "C1", "C2")
     */
    @Column(name = "target_level", length = 20)
    private String targetLevel;

    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "users_id"),
            inverseJoinColumns = @JoinColumn(name = "roles_id")
    )
    private Set<Role> roles = new LinkedHashSet<>();
}
