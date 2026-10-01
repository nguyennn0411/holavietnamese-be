package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "lessons", uniqueConstraints = @UniqueConstraint(name = "uk_lesson_order", columnNames = {"course_id", "lesson_order"}))
@Getter @Setter @NoArgsConstructor
public class LessonJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 80) private String code;
    @Column(length = 200) private String titleVi;
    @Column(length = 1000000) private String descriptionVi;
    private Long unitId;
    private Integer unitSortOrder;
    @Column(length = 20) private String publicationStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id") private CourseJpaEntity course;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 1000000) private String description;
    @Column(nullable = false) private int lessonOrder;
    @Column(nullable = false, length = 1000000) private String content;
    @Column(length = 2048) private String videoUrl;
    @Column(length = 2048) private String audioUrl;
    @Column(nullable = false) private int estimatedDuration;
    @Column(nullable = false) private boolean published;
}
