package com.sep490.backend.modules.vocabulary.service;

import static org.assertj.core.api.Assertions.*;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyRequest;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

class AdminVocabularyWorkflowTests extends ContentApiTestSupport {
    @Autowired AdminVocabularyService admin;
    @Autowired VocabularyService catalog;
    @Autowired com.sep490.backend.modules.vocabulary.repository.VocabularyRepository repository;

    @Test
    void editingPreservesSourceAndLexicalMetadata() {
        var meaning = new VocabularyMeaningRequest("hello", "greeting", null, 0, List.of());
        var request =
                new VocabularyRequest(
                        "metadata-refactor",
                        null,
                        "phrase",
                        null,
                        VocabularyStatus.DRAFT,
                        List.of(meaning));
        var draft = admin.createDraft(request);
        var word = repository.findById(draft.id()).orElseThrow();
        word.setSourceMetadata("{\"id\":\"source-id\",\"name\":\"Tu dien\",\"edited\":false}");
        word.setSourceRaw("du lieu goc");
        word.setLexicalData("{\"notes\":[\"ghi chu\"]}");
        repository.saveAndFlush(word);

        var updated = admin.updateDraft(draft.id(), request);
        assertThat(updated.source().id()).isEqualTo("source-id");
        assertThat(updated.source().edited()).isTrue();
        assertThat(updated.lexical().notes()).containsExactly("ghi chu");
        assertThat(updated.meanings().getFirst().translationEn()).isEqualTo("hello");
        assertThat(repository.findById(draft.id()).orElseThrow().getSourceRaw())
                .isEqualTo("du lieu goc");
    }

    @Test
    void draftReviewPublishAndArchiveKeepLearnerVisibilityInSync() {
        var meaning = new VocabularyMeaningRequest("hello", "greeting", "formal", 0, List.of());
        var request =
                new VocabularyRequest(
                        "refactor-workflow",
                        null,
                        "phrase",
                        null,
                        VocabularyStatus.DRAFT,
                        List.of(meaning));
        var draft = admin.createDraft(request);
        assertThat(repository.count()).isPositive();
        assertThat(draft.meanings()).hasSize(1);
        assertThatThrownBy(() -> catalog.findPublishedById(draft.id()))
                .isInstanceOf(LearningException.class);
        assertThatThrownBy(() -> admin.review(draft.id(), ContentReviewRequest.Decision.APPROVE))
                .isInstanceOf(LearningException.class);
        assertThat(admin.submitReview(draft.id()).status())
                .isEqualTo(VocabularyStatus.PENDING_REVIEW);
        assertThat(admin.review(draft.id(), ContentReviewRequest.Decision.APPROVE).status())
                .isEqualTo(VocabularyStatus.PUBLISHED);
        assertThat(catalog.findPublishedById(draft.id()).meanings().getFirst().translationEn())
                .isEqualTo("hello");
        admin.archive(draft.id());
        assertThatThrownBy(() -> catalog.findPublishedById(draft.id()))
                .isInstanceOf(LearningException.class);
        assertThat(admin.restore(draft.id()).status()).isEqualTo(VocabularyStatus.DRAFT);
    }

    @Test
    void publicSearchUsesDictionaryFieldsWhileManagementMaySearchDefinitions() {
        long definitionOnly =
                publish(
                        "lợn-search-audit",
                        "pig",
                        "A domesticated animal commonly raised for meat.");
        long unaccentedHeadword = publish("cơm-search-audit", "rice", null);
        long exactEnglish = publish("z-exact-translation-audit", "pig", null);
        long prefixEnglish = publish("a-prefix-translation-audit", "piglet", null);

        var publicCom = catalog.searchPage("com-search-audit", 0, 20, false);
        assertThat(publicCom).extracting(item -> item.id()).containsExactly(unaccentedHeadword);

        var publicDefinitionSubstring = catalog.searchPage("com", 0, 20, false);
        assertThat(publicDefinitionSubstring)
                .extracting(item -> item.id())
                .doesNotContain(definitionOnly);

        var managementDefinitionSubstring =
                admin.searchPage("com", VocabularyStatus.PUBLISHED, null, 0, 20, false);
        assertThat(managementDefinitionSubstring)
                .extracting(item -> item.id())
                .contains(definitionOnly);

        var rankedEnglish = catalog.searchPage("pig", 0, 20, false).getContent();
        assertThat(rankedEnglish).extracting(item -> item.id()).contains(exactEnglish, prefixEnglish);
        assertThat(rankedEnglish.indexOf(
                        rankedEnglish.stream()
                                .filter(item -> item.id().equals(exactEnglish))
                                .findFirst()
                                .orElseThrow()))
                .isLessThan(rankedEnglish.indexOf(
                        rankedEnglish.stream()
                                .filter(item -> item.id().equals(prefixEnglish))
                                .findFirst()
                                .orElseThrow()));

        var exactEnglishResults = catalog.searchPage("pig", 0, 20, true);
        assertThat(exactEnglishResults)
                .extracting(item -> item.id())
                .contains(exactEnglish, definitionOnly)
                .doesNotContain(prefixEnglish);

        var exactUnaccented = catalog.searchPage("com-search-audit", 0, 20, true);
        assertThat(exactUnaccented).extracting(item -> item.id()).containsExactly(unaccentedHeadword);
    }

    @Test
    void managementPageWithoutKeywordUsesStableOrderingWithoutAConditionalRank() {
        long laterAlphabetically = publish("z-empty-search-audit", "last", null);
        long earlierAlphabetically = publish("a-empty-search-audit", "first", null);

        var page = admin.searchPage(null, VocabularyStatus.PUBLISHED, null, 0, 100, false);

        var ids = page.getContent().stream().map(item -> item.id()).toList();
        assertThat(ids).contains(earlierAlphabetically, laterAlphabetically);
        assertThat(ids.indexOf(earlierAlphabetically)).isLessThan(ids.indexOf(laterAlphabetically));
    }

    private long publish(String word, String translation, String definition) {
        var request =
                new VocabularyRequest(
                        word,
                        null,
                        "noun",
                        null,
                        VocabularyStatus.DRAFT,
                        List.of(
                                new VocabularyMeaningRequest(
                                        translation, definition, null, 0, List.of())));
        var draft = admin.createDraft(request);
        admin.submitReview(draft.id());
        return admin.review(draft.id(), ContentReviewRequest.Decision.APPROVE).id();
    }
}
