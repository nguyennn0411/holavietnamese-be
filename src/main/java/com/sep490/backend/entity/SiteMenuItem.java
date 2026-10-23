package com.sep490.backend.entity;

import com.sep490.backend.entity.enums.SiteMenuPlacement;
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
@Table(name = "site_menu_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_site_menu_items_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class SiteMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SiteMenuPlacement placement;

    @Column(name = "label_vi", nullable = false, length = 200)
    private String labelVi;

    @Column(name = "label_en", nullable = false, length = 200)
    private String labelEn;

    @Column(length = 500)
    private String path;

    @Column(name = "icon_key", length = 100)
    private String iconKey;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "auth_required", nullable = false)
    private Boolean authRequired = false;

    @Column(name = "mobile_primary", nullable = false)
    private Boolean mobilePrimary = false;

    @Column(name = "feature_key", length = 100)
    private String featureKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
