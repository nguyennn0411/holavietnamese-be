package com.sep490.backend.entity;

import com.sep490.backend.entity.enums.LessonStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "user_journey_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "destination_id"}))
@Getter @Setter @NoArgsConstructor
public class JourneyProgressJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id") private DestinationJpaEntity destination;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private LessonStatus status = LessonStatus.NOT_STARTED;
    @Column(nullable = false) private int progressPercent;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastAccessedAt;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false) private Instant updatedAt;
}
