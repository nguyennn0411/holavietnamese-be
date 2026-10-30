package com.sep490.backend.modules.culture.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.culture.dto.response.CultureArticleResponse;
import com.sep490.backend.modules.culture.dto.response.CultureCategoryResponse;
import com.sep490.backend.modules.culture.dto.response.UsefulPhraseResponse;
import com.sep490.backend.modules.culture.entity.CultureArticle;
import com.sep490.backend.modules.culture.entity.CultureCategory;
import com.sep490.backend.modules.culture.repository.CultureArticleRepository;
import com.sep490.backend.modules.culture.repository.CultureCategoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CultureService {

    CultureArticleRepository articleRepository;
    CultureCategoryRepository categoryRepository;
    ObjectMapper objectMapper;

    static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public List<CultureCategoryResponse> getCategories() {
        List<CultureCategory> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        List<CultureArticle> publishedArticles = articleRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED");

        return categories.stream().map(cat -> {
            long count = publishedArticles.stream().filter(a -> matchesCategory(a, cat)).count();
            CultureCategoryResponse resp = toCategoryResponse(cat);
            resp.setArticleCount(count);
            return resp;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getDestinations() {
        return List.of(
                Map.of("id", "all", "label", "Toàn quốc", "labelVi", "Toàn quốc", "labelEn", "Nationwide"),
                Map.of("id", "hanoi", "label", "Hà Nội", "labelVi", "Hà Nội", "labelEn", "Hanoi"),
                Map.of("id", "hue", "label", "Huế", "labelVi", "Huế", "labelEn", "Hue"),
                Map.of("id", "hoian", "label", "Hội An", "labelVi", "Hội An", "labelEn", "Hoi An"),
                Map.of("id", "hcmc", "label", "TP. Hồ Chí Minh", "labelVi", "TP. Hồ Chí Minh", "labelEn", "Ho Chi Minh City"),
                Map.of("id", "mientay", "label", "Miền Tây", "labelVi", "Miền Tây", "labelEn", "Mekong Delta"),
                Map.of("id", "sapa", "label", "Sa Pa", "labelVi", "Sa Pa", "labelEn", "Sa Pa"),
                Map.of("id", "danang", "label", "Đà Nẵng", "labelVi", "Đà Nẵng", "labelEn", "Da Nang")
        );
    }

    @Transactional(readOnly = true)
    public List<CultureArticleResponse> listArticles(String search, String category, String destination) {
        List<CultureArticle> articles = articleRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED");

        return articles.stream()
                .filter(a -> {
                    if (search != null && !search.trim().isEmpty()) {
                        String q = search.trim().toLowerCase();
                        boolean match = (a.getTitle() != null && a.getTitle().toLowerCase().contains(q))
                                || (a.getTitleVi() != null && a.getTitleVi().toLowerCase().contains(q))
                                || (a.getTitleEn() != null && a.getTitleEn().toLowerCase().contains(q))
                                || (a.getSubtitle() != null && a.getSubtitle().toLowerCase().contains(q))
                                || (a.getSummary() != null && a.getSummary().toLowerCase().contains(q))
                                || (a.getAuthor() != null && a.getAuthor().toLowerCase().contains(q));
                        if (!match) return false;
                    }
                    if (category != null && !category.trim().isEmpty() && !"all".equalsIgnoreCase(category)) {
                        String catId = a.getCategory() != null ? a.getCategory().getId() : a.getCategoryName();
                        if (catId == null || !catId.equalsIgnoreCase(category.trim())) {
                            return false;
                        }
                    }
                    if (destination != null && !destination.trim().isEmpty() && !"all".equalsIgnoreCase(destination)) {
                        String dest = a.getDestinationId() != null ? a.getDestinationId() : a.getDestination();
                        if (dest == null || !dest.equalsIgnoreCase(destination.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(this::toArticleResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CultureArticleResponse> getFeaturedArticles() {
        return articleRepository.findByStatusAndIsFeaturedTrueOrderByPublishedAtDesc("PUBLISHED").stream()
                .map(this::toArticleResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CultureArticleResponse getArticle(String id) {
        CultureArticle article = articleRepository.findById(id)
                .filter(a -> "PUBLISHED".equals(a.getStatus()))
                .orElseThrow(() -> new ContentException("NOT_FOUND", "Không tìm thấy bài viết văn hóa: " + id, org.springframework.http.HttpStatus.NOT_FOUND));

        try {
            articleRepository.incrementViews(id);
        } catch (Exception e) {
            log.warn("Không thể tăng lượt xem cho bài viết: {}", id, e);
        }

        CultureArticleResponse response = toArticleResponse(article);
        response.setRelatedArticles(getRelatedArticles(article));
        return response;
    }

    @Transactional(readOnly = true)
    public List<CultureArticleResponse> getRelatedArticles(CultureArticle article) {
        List<String> relatedIds = parseStringList(article.getRelatedArticleIdsJson());
        List<CultureArticle> candidates = articleRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED");

        return candidates.stream()
                .filter(c -> !c.getId().equals(article.getId()))
                .filter(c -> relatedIds.contains(c.getId())
                        || (article.getCategory() != null && c.getCategory() != null && c.getCategory().getId().equals(article.getCategory().getId()))
                        || (article.getDestinationId() != null && article.getDestinationId().equals(c.getDestinationId())))
                .limit(3)
                .map(this::toArticleResponse)
                .collect(Collectors.toList());
    }

    public boolean matchesCategory(CultureArticle article, CultureCategory category) {
        if (article == null || category == null) return false;
        if (article.getCategory() != null) {
            if (category.getId() != null && category.getId().equalsIgnoreCase(article.getCategory().getId())) return true;
            if (category.getSlug() != null && category.getSlug().equalsIgnoreCase(article.getCategory().getSlug())) return true;
        }
        String aCatName = article.getCategoryName();
        if (aCatName != null && !aCatName.trim().isEmpty()) {
            String name = aCatName.trim();
            if (category.getId() != null && category.getId().equalsIgnoreCase(name)) return true;
            if (category.getSlug() != null && category.getSlug().equalsIgnoreCase(name)) return true;
            if (category.getName() != null && category.getName().equalsIgnoreCase(name)) return true;
            if (category.getNameVi() != null && category.getNameVi().equalsIgnoreCase(name)) return true;
            if (category.getNameEn() != null && category.getNameEn().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public CultureCategoryResponse toCategoryResponse(CultureCategory category) {
        return CultureCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .nameVi(category.getNameVi() != null ? category.getNameVi() : category.getName())
                .nameEn(category.getNameEn() != null ? category.getNameEn() : category.getName())
                .slug(category.getSlug())
                .icon(category.getIcon())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .articleCount(0L)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public CultureArticleResponse toArticleResponse(CultureArticle a) {
        String catId = a.getCategory() != null ? a.getCategory().getId() : "culinary";
        String catName = a.getCategory() != null ? a.getCategory().getName() : (a.getCategoryName() != null ? a.getCategoryName() : "Văn hóa");
        String catNameVi = a.getCategory() != null && a.getCategory().getNameVi() != null ? a.getCategory().getNameVi() : catName;
        String catNameEn = a.getCategory() != null && a.getCategory().getNameEn() != null ? a.getCategory().getNameEn() : catName;

        String publishedDate = a.getPublishedAt() != null ? a.getPublishedAt().format(DATE_FORMATTER) : (a.getCreatedAt() != null ? a.getCreatedAt().format(DATE_FORMATTER) : "Mới");

        List<UsefulPhraseResponse> phrases = parseUsefulPhrases(a.getUsefulPhrasesJson());
        List<String> relatedIds = parseStringList(a.getRelatedArticleIdsJson());

        return CultureArticleResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .titleVi(a.getTitleVi() != null ? a.getTitleVi() : a.getTitle())
                .titleEn(a.getTitleEn() != null ? a.getTitleEn() : a.getTitle())
                .subtitle(a.getSubtitle())
                .subtitleVi(a.getSubtitleVi() != null ? a.getSubtitleVi() : a.getSubtitle())
                .subtitleEn(a.getSubtitleEn() != null ? a.getSubtitleEn() : a.getSubtitle())
                .summary(a.getSummary())
                .summaryVi(a.getSummaryVi() != null ? a.getSummaryVi() : a.getSummary())
                .summaryEn(a.getSummaryEn() != null ? a.getSummaryEn() : a.getSummary())
                .content(a.getContent())
                .contentVi(a.getContentVi() != null ? a.getContentVi() : a.getContent())
                .contentEn(a.getContentEn() != null ? a.getContentEn() : a.getContent())
                .category(catId)
                .categoryName(catName)
                .categoryNameVi(catNameVi)
                .categoryNameEn(catNameEn)
                .region(a.getRegion())
                .destination(a.getDestination())
                .destinationId(a.getDestinationId())
                .destinationName(a.getDestination())
                .destinationNameVi(a.getDestination())
                .destinationNameEn(a.getDestination())
                .author(a.getAuthor())
                .authorRole(a.getAuthorRole())
                .authorRoleVi(a.getAuthorRoleVi() != null ? a.getAuthorRoleVi() : a.getAuthorRole())
                .authorRoleEn(a.getAuthorRoleEn() != null ? a.getAuthorRoleEn() : a.getAuthorRole())
                .readTime(a.getReadTime())
                .readTimeVi(a.getReadTimeVi() != null ? a.getReadTimeVi() : a.getReadTime())
                .readTimeEn(a.getReadTimeEn() != null ? a.getReadTimeEn() : a.getReadTime())
                .coverImage(a.getCoverImage())
                .status(a.getStatus())
                .isFeatured(a.getIsFeatured())
                .views(a.getViews())
                .publishedAt(a.getPublishedAt())
                .publishedDate(publishedDate)
                .usefulPhrases(phrases)
                .relatedArticleIds(relatedIds)
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private List<UsefulPhraseResponse> parseUsefulPhrases(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<UsefulPhraseResponse>>() {});
        } catch (Exception e) {
            log.warn("Không thể parse usefulPhrases JSON: {}", json);
            return Collections.emptyList();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
