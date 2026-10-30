package com.sep490.backend.modules.admin.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "site_feature_flags")
@Getter
@Setter
@NoArgsConstructor
public class SiteFeatureFlag {

    @Id
    @Column(name = "feature_key", length = 100)
    private String key;

    @Column(name = "name_vi", nullable = false, length = 200)
    private String nameVi;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
