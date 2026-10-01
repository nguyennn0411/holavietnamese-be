package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.sep490.backend.entity.enums.CourseStatus;
@Entity @Table(name = "courses")
@Getter @Setter @NoArgsConstructor
public class CourseJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 80) private String code;
    @Column(length = 200) private String titleVi;
    @Column(length = 1000000) private String descriptionVi;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1000000) private String description;
    @Column(length = 2048) private String thumbnailUrl;
    @Column(nullable = false, length = 30) private String level;
    @Column(nullable = false) private int estimatedDuration;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CourseStatus status;
}
