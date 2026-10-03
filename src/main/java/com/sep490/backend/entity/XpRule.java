package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "xp_rules")
public class XpRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, unique = true, length = 50)
    private String eventType;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 10;

    @Column(name = "daily_cap")
    private Integer dailyCap = 100;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
