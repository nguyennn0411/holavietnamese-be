package com.sep490.backend.modules.culture.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.exception.ContentException;
import org.springframework.http.HttpStatus;
import com.sep490.backend.modules.culture.dto.request.CultureArticleRequest;
import com.sep490.backend.modules.culture.dto.request.CultureCategoryRequest;
import com.sep490.backend.modules.culture.dto.response.CultureArticleResponse;
import com.sep490.backend.modules.culture.dto.response.CultureCategoryResponse;
import com.sep490.backend.modules.culture.entity.CultureArticle;
import com.sep490.backend.modules.culture.entity.CultureCategory;
import com.sep490.backend.modules.culture.repository.CultureArticleRepository;
import com.sep490.backend.modules.culture.repository.CultureCategoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AdminCultureService {

    CultureArticleRepository articleRepository;
    CultureCategoryRepository categoryRepository;
    CultureService cultureService;
    ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CultureArticleResponse> getAllArticles(String search, String category, String status) {
        List<CultureArticle> list = articleRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return list.stream()
                .filter(a -> {
                    if (search != null && !search.trim().isEmpty()) {
                        String q = search.trim().toLowerCase();
                        boolean match = (a.getTitle() != null && a.getTitle().toLowerCase().contains(q))
                                || (a.getTitleVi() != null && a.getTitleVi().toLowerCase().contains(q))
                                || (a.getAuthor() != null && a.getAuthor().toLowerCase().contains(q));
                        if (!match) return false;
                    }
                    if (category != null && !category.trim().isEmpty() && !"all".equalsIgnoreCase(category)) {
                        String filterCat = category.trim();
                        boolean match = false;
                        if (a.getCategory() != null) {
                            if (filterCat.equalsIgnoreCase(a.getCategory().getId())
                                    || (a.getCategory().getSlug() != null && filterCat.equalsIgnoreCase(a.getCategory().getSlug()))) {
                                match = true;
                            }
                        }
                        if (!match && a.getCategoryName() != null) {
                            if (filterCat.equalsIgnoreCase(a.getCategoryName())) {
                                match = true;
                            }
                        }
                        if (!match) return false;
                    }
                    if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status)) {
                        if (a.getStatus() == null || !a.getStatus().equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(cultureService::toArticleResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CultureArticleResponse getArticle(String id) {
        CultureArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ContentException("NOT_FOUND", "Không tìm thấy bài viết: " + id, HttpStatus.NOT_FOUND));
        return cultureService.toArticleResponse(article);
    }

    @Transactional
    public CultureArticleResponse saveArticle(CultureArticleRequest request) {
        String articleId = request.getId();
        if (articleId == null || articleId.trim().isEmpty() || "new".equalsIgnoreCase(articleId)) {
            articleId = "culture-" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            articleId = articleId.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        }

        CultureArticle article = articleRepository.findById(articleId)
                .orElse(CultureArticle.builder().id(articleId).build());

        article.setTitle(request.getTitle());
        article.setTitleVi(request.getTitleVi() != null ? request.getTitleVi() : request.getTitle());
        article.setTitleEn(request.getTitleEn() != null ? request.getTitleEn() : request.getTitle());
        article.setSubtitle(request.getSubtitle());
        article.setSubtitleVi(request.getSubtitleVi() != null ? request.getSubtitleVi() : request.getSubtitle());
        article.setSubtitleEn(request.getSubtitleEn() != null ? request.getSubtitleEn() : request.getSubtitle());
        article.setSummary(request.getSummary());
        article.setSummaryVi(request.getSummaryVi() != null ? request.getSummaryVi() : request.getSummary());
        article.setSummaryEn(request.getSummaryEn() != null ? request.getSummaryEn() : request.getSummary());
        article.setContent(request.getContent());
        article.setContentVi(request.getContentVi() != null ? request.getContentVi() : request.getContent());
        article.setContentEn(request.getContentEn() != null ? request.getContentEn() : request.getContent());

        if (request.getCategory() != null) {
            String catParam = request.getCategory().trim();
            CultureCategory cat = categoryRepository.findById(catParam)
                    .or(() -> categoryRepository.findBySlug(catParam))
                    .orElse(null);
            article.setCategory(cat);
            article.setCategoryName(cat != null ? cat.getName() : request.getCategoryName());
        }

        article.setRegion(request.getRegion() != null ? request.getRegion() : "Toàn quốc");
        article.setDestination(request.getDestination() != null ? request.getDestination() : "Toàn quốc");
        article.setDestinationId(request.getDestinationId() != null ? request.getDestinationId() : "all");
        article.setAuthor(request.getAuthor() != null ? request.getAuthor() : "Biên tập viên Hola");
        article.setAuthorRole(request.getAuthorRole());
        article.setAuthorRoleVi(request.getAuthorRoleVi());
        article.setAuthorRoleEn(request.getAuthorRoleEn());
        article.setReadTime(request.getReadTime() != null ? request.getReadTime() : "5 phút đọc");
        article.setReadTimeVi(request.getReadTimeVi() != null ? request.getReadTimeVi() : "5 phút đọc");
        article.setReadTimeEn(request.getReadTimeEn() != null ? request.getReadTimeEn() : "5 min read");
        article.setCoverImage(request.getCoverImage());
        article.setStatus(request.getStatus() != null ? request.getStatus() : "PUBLISHED");
        article.setIsFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false);

        if ("PUBLISHED".equals(article.getStatus()) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }

        if (request.getUsefulPhrases() != null) {
            try {
                article.setUsefulPhrasesJson(objectMapper.writeValueAsString(request.getUsefulPhrases()));
            } catch (JsonProcessingException e) {
                log.warn("Lỗi serialize usefulPhrases", e);
            }
        }

        if (request.getRelatedArticleIds() != null) {
            try {
                article.setRelatedArticleIdsJson(objectMapper.writeValueAsString(request.getRelatedArticleIds()));
            } catch (JsonProcessingException e) {
                log.warn("Lỗi serialize relatedArticleIds", e);
            }
        }

        CultureArticle saved = articleRepository.save(article);
        return cultureService.toArticleResponse(saved);
    }

    @Transactional
    public CultureArticleResponse updateArticleStatus(String id, String status) {
        CultureArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ContentException("NOT_FOUND", "Không tìm thấy bài viết: " + id, HttpStatus.NOT_FOUND));

        article.setStatus(status != null ? status.toUpperCase() : "DRAFT");
        if ("PUBLISHED".equalsIgnoreCase(status) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        CultureArticle saved = articleRepository.save(article);
        return cultureService.toArticleResponse(saved);
    }

    @Transactional
    public void deleteArticle(String id) {
        if (!articleRepository.existsById(id)) {
            throw new ContentException("NOT_FOUND", "Không tìm thấy bài viết: " + id, HttpStatus.NOT_FOUND);
        }
        articleRepository.deleteById(id);
    }

    // --- CATEGORY MANAGEMENT ---

    @Transactional(readOnly = true)
    public List<CultureCategoryResponse> getAllCategories() {
        List<CultureCategory> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        List<CultureArticle> allArticles = articleRepository.findAll();

        return categories.stream().map(cat -> {
            long count = allArticles.stream().filter(a -> cultureService.matchesCategory(a, cat)).count();
            CultureCategoryResponse resp = cultureService.toCategoryResponse(cat);
            resp.setArticleCount(count);
            return resp;
        }).collect(Collectors.toList());
    }

    @Transactional
    public CultureCategoryResponse saveCategory(CultureCategoryRequest request) {
        String catId = request.getId();
        if (catId == null || catId.trim().isEmpty()) {
            catId = request.getSlug() != null && !request.getSlug().trim().isEmpty()
                    ? request.getSlug().trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-")
                    : "cat-" + UUID.randomUUID().toString().substring(0, 8);
        }

        CultureCategory category = categoryRepository.findById(catId)
                .orElse(CultureCategory.builder().id(catId).build());

        category.setName(request.getName());
        category.setNameVi(request.getNameVi() != null ? request.getNameVi() : request.getName());
        category.setNameEn(request.getNameEn() != null ? request.getNameEn() : request.getName());
        category.setSlug(request.getSlug() != null ? request.getSlug() : catId);
        category.setIcon(request.getIcon() != null ? request.getIcon() : "🏮");
        category.setDescription(request.getDescription());
        category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        CultureCategory saved = categoryRepository.save(category);
        CultureCategoryResponse resp = cultureService.toCategoryResponse(saved);
        long count = articleRepository.findAll().stream().filter(a -> cultureService.matchesCategory(a, saved)).count();
        resp.setArticleCount(count);
        return resp;
    }

    @Transactional
    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ContentException("NOT_FOUND", "Không tìm thấy danh mục: " + id, HttpStatus.NOT_FOUND);
        }
        categoryRepository.deleteById(id);
    }
}
