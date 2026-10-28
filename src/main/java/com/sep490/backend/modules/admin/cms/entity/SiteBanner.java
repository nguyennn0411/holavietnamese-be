package com.sep490.backend.modules.admin.cms.entity;

import com.sep490.backend.modules.admin.cms.entity.enums.SiteBannerActionType;
import com.sep490.backend.modules.admin.cms.entity.enums.SiteBannerPlacement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "site_banners", uniqueConstraints =
        @UniqueConstraint(name = "uk_site_banners_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class SiteBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SiteBannerPlacement placement;

    @Column(name = "eyebrow_vi", length = 255)
    private String eyebrowVi;

    @Column(name = "eyebrow_en", length = 255)
    private String eyebrowEn;

    @Column(name = "title_vi", nullable = false, length = 255)
    private String titleVi;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "description_vi", length = 2000)
    private String descriptionVi;

    @Column(name = "description_en", length = 2000)
    private String descriptionEn;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "image_alt_vi", length = 500)
    private String imageAltVi;

    @Column(name = "image_alt_en", length = 500)
    private String imageAltEn;

    @Column(name = "photo_label_vi", length = 255)
    private String photoLabelVi;

    @Column(name = "photo_label_en", length = 255)
    private String photoLabelEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private SiteBannerActionType actionType = SiteBannerActionType.NONE;

    @Column(name = "action_label_vi", length = 255)
    private String actionLabelVi;

    @Column(name = "action_label_en", length = 255)
    private String actionLabelEn;

    @Column(name = "action_target", length = 2048)
    private String actionTarget;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "feature_key", length = 100)
    private String featureKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
