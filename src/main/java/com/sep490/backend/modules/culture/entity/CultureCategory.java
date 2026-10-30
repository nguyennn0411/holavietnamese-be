package com.sep490.backend.modules.culture.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "culture_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CultureCategory {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_vi")
    private String nameVi;

    @Column(name = "name_en")
    private String nameEn;

    @Column(length = 100)
    private String slug;

    @Column(length = 50)
    @Builder.Default
    private String icon = "🏮";

    @Column(length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
