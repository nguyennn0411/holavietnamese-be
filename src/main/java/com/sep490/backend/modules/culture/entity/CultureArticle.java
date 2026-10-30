package com.sep490.backend.modules.culture.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "culture_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CultureArticle {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "title_vi", length = 500)
    private String titleVi;

    @Column(name = "title_en", length = 500)
    private String titleEn;

    @Column(length = 1000)
    private String subtitle;

    @Column(name = "subtitle_vi", length = 1000)
    private String subtitleVi;

    @Column(name = "subtitle_en", length = 1000)
    private String subtitleEn;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "summary_vi", columnDefinition = "TEXT")
    private String summaryVi;

    @Column(name = "summary_en", columnDefinition = "TEXT")
    private String summaryEn;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "content_vi", columnDefinition = "LONGTEXT")
    private String contentVi;

    @Column(name = "content_en", columnDefinition = "LONGTEXT")
    private String contentEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CultureCategory category;

    @Column(name = "category_name")
    private String categoryName;

    @Column(length = 100)
    @Builder.Default
    private String region = "Toàn quốc";

    @Column(length = 100)
    @Builder.Default
    private String destination = "Toàn quốc";

    @Column(name = "destination_id", length = 100)
    @Builder.Default
    private String destinationId = "all";

    @Column(length = 255)
    @Builder.Default
    private String author = "Biên tập viên Hola";

    @Column(name = "author_role", length = 255)
    private String authorRole;

    @Column(name = "author_role_vi", length = 255)
    private String authorRoleVi;

    @Column(name = "author_role_en", length = 255)
    private String authorRoleEn;

    @Column(name = "read_time", length = 100)
    @Builder.Default
    private String readTime = "5 phút đọc";

    @Column(name = "read_time_vi", length = 100)
    @Builder.Default
    private String readTimeVi = "5 phút đọc";

    @Column(name = "read_time_en", length = 100)
    @Builder.Default
    private String readTimeEn = "5 min read";

    @Column(name = "cover_image", length = 1000)
    private String coverImage;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String status = "PUBLISHED"; // DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(nullable = false)
    @Builder.Default
    private Long views = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "useful_phrases", columnDefinition = "JSON")
    private String usefulPhrasesJson;

    @Column(name = "related_article_ids", columnDefinition = "JSON")
    private String relatedArticleIdsJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
