package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "destination_lessons", uniqueConstraints = @UniqueConstraint(columnNames = {"destination_id", "lesson_id"}))
@Getter @Setter @NoArgsConstructor
public class DestinationLessonJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long destinationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id") private LessonJpaEntity lesson;
    @Column(nullable = false) private int displayOrder;
}
