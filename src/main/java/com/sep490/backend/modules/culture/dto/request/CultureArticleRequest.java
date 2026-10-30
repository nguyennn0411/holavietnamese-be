package com.sep490.backend.modules.culture.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CultureArticleRequest {
    private String id;

    @NotBlank(message = "Tiêu đề bài viết không được để trống")
    private String title;

    private String titleVi;
    private String titleEn;
    private String subtitle;
    private String subtitleVi;
    private String subtitleEn;
    private String summary;
    private String summaryVi;
    private String summaryEn;
    private String content;
    private String contentVi;
    private String contentEn;

    private String category;
    private String categoryName;
    private String region;
    private String destination;
    private String destinationId;

    private String author;
    private String authorRole;
    private String authorRoleVi;
    private String authorRoleEn;

    private String readTime;
    private String readTimeVi;
    private String readTimeEn;

    private String coverImage;
    private String status; // PUBLISHED, IN_REVIEW, DRAFT, ARCHIVED
    private Boolean isFeatured;

    private List<UsefulPhraseRequest> usefulPhrases;
    private List<String> relatedArticleIds;
}
