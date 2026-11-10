package com.sep490.backend.modules.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.culture.entity.CultureArticle;
import com.sep490.backend.modules.culture.repository.CultureArticleRepository;
import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.DestinationVocabulary;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.DestinationRepository;
import com.sep490.backend.modules.explore.repository.DestinationVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Unit tests for Explore visibility and reference validation before link replacement. */
class DestinationExploreServiceTests {
    private DestinationRepository destinations;
    private DestinationVocabularyRepository links;
    private AdminVocabularyService words;
    private AdminRegionalVocabularyService adminRegional;
    private AiScenarioRepository scenarios;
    private CultureArticleRepository cultures;
    private RegionalVocabularyService regional;
    private JdbcTemplate jdbc;
    private EntityManager em;
    private DestinationExploreService service;

    @BeforeEach
    void setUp() {
        destinations = mock(DestinationRepository.class);
        links = mock(DestinationVocabularyRepository.class);
        words = mock(AdminVocabularyService.class);
        adminRegional = mock(AdminRegionalVocabularyService.class);
        scenarios = mock(AiScenarioRepository.class);
        cultures = mock(CultureArticleRepository.class);
        regional = mock(RegionalVocabularyService.class);
        jdbc = mock(JdbcTemplate.class);
        em = mock(EntityManager.class);
        service =
                new DestinationExploreService(
                        destinations, links, words, adminRegional, scenarios, cultures,
                        regional, jdbc, em);
    }

    @Test
    void publicContentHidesDraftDestinationWhileManagementCanReadIt() {
        var draft = destination(1L, DestinationStatus.DRAFT);
        when(destinations.findById(1L)).thenReturn(Optional.of(draft));

        assertKind(() -> service.content(1L, false), LearningException.Kind.NOT_FOUND);
        assertThat(service.content(1L, true).vocabulary()).isEmpty();
    }

    @Test
    void publicContentFiltersDraftWordsAndInactiveScenariosButPreservesOrder() {
        var publishedDestination = destination(1L, DestinationStatus.PUBLISHED);
        var publishedWord = word(10L, "xin", VocabularyStatus.PUBLISHED);
        var draftWord = word(11L, "hidden", VocabularyStatus.DRAFT);
        when(destinations.findById(1L)).thenReturn(Optional.of(publishedDestination));
        when(links.findByDestinationIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(link(1L, publishedWord, 0), link(1L, draftWord, 1)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq("SCENARIO")))
                .thenReturn(List.of("active", "inactive"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq("CULTURE")))
                .thenReturn(List.of("culture-1"));
        var active = scenario("active", "ACTIVE");
        var inactive = scenario("inactive", "DRAFT");
        when(scenarios.findAllById(List.of("active", "inactive")))
                .thenReturn(List.of(inactive, active));

        var response = service.content(1L, false);

        assertThat(response.vocabulary()).extracting(row -> row.id()).containsExactly(10L);
        assertThat(response.scenarios()).extracting(row -> row.id()).containsExactly("active");
        assertThat(response.cultureIds()).containsExactly("culture-1");
        verify(regional).forDestination(List.of(10L), VietnamRegion.NORTH);
    }

    @Test
    void saveRejectsMissingDestinationAndDuplicateReferencesBeforeMutation() {
        var request = new DestinationExploreContentRequest(List.of(10L, 10L), List.of(), List.of());
        when(em.find(Destination.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertKind(() -> service.save(1L, request), LearningException.Kind.NOT_FOUND);

        when(em.find(Destination.class, 1L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(destination(1L, DestinationStatus.DRAFT));
        assertKind(() -> service.save(1L, request), LearningException.Kind.INVALID);
        verify(links, never()).deleteAll(any());
    }

    @Test
    void saveRejectsMissingWordScenarioAndUnpublishedCulture() {
        when(em.find(Destination.class, 1L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(destination(1L, DestinationStatus.DRAFT));

        var missingWord = new DestinationExploreContentRequest(List.of(10L), List.of(), List.of());
        when(words.findExistingWords(List.of(10L))).thenReturn(List.of());
        assertKind(() -> service.save(1L, missingWord), LearningException.Kind.NOT_FOUND);

        var word = word(10L, "xin", VocabularyStatus.DRAFT);
        when(words.findExistingWords(List.of(10L))).thenReturn(List.of(word));
        var missingScenario =
                new DestinationExploreContentRequest(List.of(10L), List.of(), List.of("missing"));
        when(scenarios.findAllById(List.of("missing"))).thenReturn(List.of());
        assertKind(() -> service.save(1L, missingScenario), LearningException.Kind.NOT_FOUND);

        var article = new CultureArticle();
        article.setId("draft-culture");
        article.setStatus("DRAFT");
        var unpublishedCulture =
                new DestinationExploreContentRequest(List.of(10L), List.of("draft-culture"), List.of());
        when(cultures.findAllById(List.of("draft-culture"))).thenReturn(List.of(article));
        assertKind(() -> service.save(1L, unpublishedCulture), LearningException.Kind.NOT_FOUND);
    }

    private static Destination destination(long id, DestinationStatus status) {
        var destination = new Destination();
        destination.setId(id);
        destination.setNameVi("Hà Nội");
        destination.setNameEn("Hanoi");
        destination.setSlug("ha-noi");
        destination.setRegion(VietnamRegion.NORTH);
        destination.setStatus(status);
        return destination;
    }

    private static VocabularyWord word(long id, String text, VocabularyStatus status) {
        var word = new VocabularyWord();
        word.setId(id);
        word.setWord(text);
        word.setPartOfSpeech("noun");
        word.setStatus(status);
        return word;
    }

    private static DestinationVocabulary link(long destinationId, VocabularyWord word, int order) {
        var link = new DestinationVocabulary();
        link.setDestinationId(destinationId);
        link.setVocabulary(word);
        link.setDisplayOrder(order);
        return link;
    }

    private static AiScenario scenario(String id, String status) {
        var scenario = new AiScenario();
        scenario.setId(id);
        scenario.setTitle(id);
        scenario.setStatus(status);
        return scenario;
    }

    private static void assertKind(Runnable action, LearningException.Kind expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(expected));
    }
}
