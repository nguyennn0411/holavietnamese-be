package com.sep490.backend.modules.culture.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.culture.dto.response.CultureArticleResponse;
import com.sep490.backend.modules.culture.dto.response.CultureCategoryResponse;
import com.sep490.backend.modules.culture.service.CultureService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/culture")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CultureController {

    CultureService cultureService;

    @GetMapping("/categories")
    public ApiResponse<List<CultureCategoryResponse>> getCategories() {
        return ApiResponse.<List<CultureCategoryResponse>>builder()
                .result(cultureService.getCategories())
                .build();
    }

    @GetMapping("/destinations")
    public ApiResponse<List<Map<String, String>>> getDestinations() {
        return ApiResponse.<List<Map<String, String>>>builder()
                .result(cultureService.getDestinations())
                .build();
    }

    @GetMapping("/articles")
    public ApiResponse<List<CultureArticleResponse>> listArticles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "all") String destination
    ) {
        return ApiResponse.<List<CultureArticleResponse>>builder()
                .result(cultureService.listArticles(search, category, destination))
                .build();
    }

    @GetMapping("/articles/featured")
    public ApiResponse<List<CultureArticleResponse>> getFeaturedArticles() {
        return ApiResponse.<List<CultureArticleResponse>>builder()
                .result(cultureService.getFeaturedArticles())
                .build();
    }

    @GetMapping("/articles/batch")
    public ApiResponse<List<CultureArticleResponse>> getArticlesByIds(
            @RequestParam List<String> ids) {
        return ApiResponse.<List<CultureArticleResponse>>builder()
                .result(cultureService.listPublishedByIds(ids))
                .build();
    }

    @GetMapping("/articles/{id}")
    public ApiResponse<CultureArticleResponse> getArticle(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.<CultureArticleResponse>builder()
                .result(cultureService.getArticle(id, resolveViewerKey(request)))
                .build();
    }

    private String resolveViewerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "user:" + auth.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        String agent = request.getHeader("User-Agent");
        return "guest:" + ip + "|" + (agent != null ? agent.hashCode() : 0);
    }
}
