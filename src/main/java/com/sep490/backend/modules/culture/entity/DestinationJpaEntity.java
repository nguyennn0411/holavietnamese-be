package com.sep490.backend.modules.culture.entity;

import com.sep490.backend.modules.culture.entity.enums.DestinationStatus;
import com.sep490.backend.modules.culture.entity.enums.VietnamRegion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "destinations", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_destinations_slug",
                columnNames = "slug"
        )
})
@Getter
@Setter
@NoArgsConstructor
public class DestinationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_vi", nullable = false, length = 150)
    private String nameVi;

    @Column(name = "name_en", nullable = false, length = 150)
    private String nameEn;

    @Column(nullable = false, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VietnamRegion region;

    @Column(name = "short_description_vi", length = 500)
    private String shortDescriptionVi;

    @Column(name = "short_description_en", length = 500)
    private String shortDescriptionEn;

    @Column(name = "description_vi", columnDefinition = "TEXT")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DestinationStatus status = DestinationStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
