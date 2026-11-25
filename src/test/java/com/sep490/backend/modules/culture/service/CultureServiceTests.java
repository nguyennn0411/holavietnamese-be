package com.sep490.backend.modules.culture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.culture.entity.CultureArticle;
import com.sep490.backend.modules.culture.repository.CultureArticleRepository;
import com.sep490.backend.modules.culture.repository.CultureCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CultureServiceTests {
    private final CultureArticleRepository articles = mock(CultureArticleRepository.class);
    private final CultureService service =
            new CultureService(articles, mock(CultureCategoryRepository.class), new ObjectMapper());

    @Test
    void batchKeepsRequestedOrderAndOnlyReturnsPublishedArticles() {
        var first = article("first", "PUBLISHED");
        var second = article("second", "PUBLISHED");
        var draft = article("draft", "DRAFT");
        when(articles.findAllById(List.of("second", "draft", "first")))
                .thenReturn(List.of(first, draft, second));

        assertThat(service.listPublishedByIds(List.of("second", "draft", "first")))
                .extracting(response -> response.getId())
                .containsExactly("second", "first");
    }

    @Test
    void batchRejectsDuplicatesAndOversizedRequests() {
        assertThatThrownBy(() -> service.listPublishedByIds(List.of("same", "same")))
                .isInstanceOf(LearningException.class);
        assertThatThrownBy(
                        () ->
                                service.listPublishedByIds(
                                        java.util.stream.IntStream.range(0, 101)
                                                .mapToObj(String::valueOf)
                                                .toList()))
                .isInstanceOf(LearningException.class);
    }

    @Test
    void getArticleCountsOneRealViewPerViewerAndDeduplicatesReloads() {
        var published = article("van-hoa-ca-phe-viet-nam", "PUBLISHED");
        published.setViews(0L);
        when(articles.findById("van-hoa-ca-phe-viet-nam")).thenReturn(Optional.of(published));
        when(articles.findByStatusOrderByPublishedAtDesc("PUBLISHED")).thenReturn(List.of(published));

        var firstRead = service.getArticle("van-hoa-ca-phe-viet-nam", "user:learner");
        assertThat(firstRead.getViews()).isEqualTo(1L);

        // Simulate stored DB state after increment
        published.setViews(1L);

        // Immediate second read by the same viewer does not increment again
        var duplicateRead = service.getArticle("van-hoa-ca-phe-viet-nam", "user:learner");
        assertThat(duplicateRead.getViews()).isEqualTo(1L);
        verify(articles, times(1)).incrementViews("van-hoa-ca-phe-viet-nam");

        // A different viewer increments the counter to 2
        var secondViewerRead = service.getArticle("van-hoa-ca-phe-viet-nam", "guest:127.0.0.1|123");
        assertThat(secondViewerRead.getViews()).isEqualTo(2L);
        verify(articles, times(2)).incrementViews("van-hoa-ca-phe-viet-nam");
    }

    private static CultureArticle article(String id, String status) {
        var article = new CultureArticle();
        article.setId(id);
        article.setTitle(id);
        article.setStatus(status);
        return article;
    }
}
