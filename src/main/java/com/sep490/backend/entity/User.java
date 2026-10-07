package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private Long id;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ColumnDefault("true")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @ColumnDefault("'LEARNER'")
    @Column(name = "role", nullable = false, length = 20)
    private String role = "LEARNER";

    @Column(name = "email", length = 100, unique = true, nullable = false)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "country", length = 100)
    private String country;

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

    @Column(name = "daily_learning_goal_minutes")
    private Integer dailyLearningGoalMinutes = 15;

    @Column(name = "audio_speed")
    private Double audioSpeed = 1.0;

    @Column(name = "pronunciation_hints_enabled")
    private Boolean pronunciationHintsEnabled = true;

    @Column(name = "auto_translate_enabled")
    private Boolean autoTranslateEnabled = true;

    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = true;

    @Column(name = "onboarding_completed")
    private Boolean onboardingCompleted = false;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "email_verification_token", length = 100)
    private String emailVerificationToken;

    @Column(name = "verification_token_expiry")
    private LocalDateTime verificationTokenExpiry;

    @Column(name = "reset_password_otp", length = 10)
    private String resetPasswordOtp;

    @Column(name = "reset_password_otp_expiry")
    private LocalDateTime resetPasswordOtpExpiry;

    @Column(name = "streak_count")
    private Integer streakCount = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "total_xp")
    private Integer totalXp = 0;

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
