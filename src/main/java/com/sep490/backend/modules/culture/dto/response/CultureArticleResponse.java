package com.sep490.backend.modules.culture.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CultureArticleResponse {
    private String id;
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
    private String categoryNameVi;
    private String categoryNameEn;

    private String region;
    private String destination;
    private String destinationId;
    private String destinationName;
    private String destinationNameVi;
    private String destinationNameEn;

    private String author;
    private String authorRole;
    private String authorRoleVi;
    private String authorRoleEn;

    private String readTime;
    private String readTimeVi;
    private String readTimeEn;

    private String coverImage;
    private String status;
    private Boolean isFeatured;
    private Long views;
    private LocalDateTime publishedAt;
    private String publishedDate;

    private List<UsefulPhraseResponse> usefulPhrases;
    private List<String> relatedArticleIds;
    private List<CultureArticleResponse> relatedArticles;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
