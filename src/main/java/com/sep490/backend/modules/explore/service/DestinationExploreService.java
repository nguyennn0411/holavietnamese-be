package com.sep490.backend.modules.explore.service;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.culture.repository.CultureArticleRepository;
import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.dto.response.DestinationExploreContentResponse;
import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.DestinationVocabulary;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.mapper.DestinationMapper;
import com.sep490.backend.modules.explore.repository.DestinationRepository;
import com.sep490.backend.modules.explore.repository.DestinationVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Doc noi dung kham pha doc lap voi tien do va phan thuong khoa hoc. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationExploreService {
    private final DestinationRepository destinations;
    private final DestinationVocabularyRepository links;
    private final AdminVocabularyService words;
    private final AdminRegionalVocabularyService adminRegional;
    private final AiScenarioRepository scenarios;
    private final CultureArticleRepository cultureArticles;
    private final RegionalVocabularyService regional;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    /** Lay noi dung dia diem theo che do nguoi hoc hoac quan tri. */
    public DestinationExploreContentResponse content(long id, boolean management) {
        return content(id, management, 0);
    }

    /** Lay noi dung dia diem; nguoi hoc nhan 10 bien the vung mien moi trang. */
    public DestinationExploreContentResponse content(long id, boolean management, int regionalPage) {
        Destination destination =
                destinations
                        .findById(id)
                        .orElseThrow(() -> LearningException.notFound("Destination"));
        if (!management && destination.getStatus() != DestinationStatus.PUBLISHED) {
            throw LearningException.notFound("Destination");
        }

        List<DestinationExploreContentResponse.Word> vocabulary = new ArrayList<>();
        List<Long> vocabularyIds = new ArrayList<>();
        for (DestinationVocabulary link :
                links.findByDestinationIdOrderByDisplayOrderAscIdAsc(id)) {
            VocabularyWord word = link.getVocabulary();
            if (management || word.getStatus() == VocabularyStatus.PUBLISHED) {
                vocabulary.add(
                        new DestinationExploreContentResponse.Word(
                                word.getId(),
                                word.getWord(),
                                word.getPronunciation(),
                                word.getStatus().name()));
                vocabularyIds.add(word.getId());
            }
        }

        List<String> scenarioIds = references(id, "SCENARIO");
        Map<String, AiScenario> available = new HashMap<>();
        for (AiScenario scenario : scenarios.findAllById(scenarioIds)) {
            available.put(scenario.getId(), scenario);
        }
        List<DestinationExploreContentResponse.Scenario> scenarioRows = new ArrayList<>();
        for (String scenarioId : scenarioIds) {
            AiScenario scenario = available.get(scenarioId);
            if (scenario == null || (!management && !"ACTIVE".equals(scenario.getStatus()))) {
                continue;
            }
            scenarioRows.add(DestinationMapper.toScenarioResponse(scenario));
        }

        List<com.sep490.backend.modules.explore.dto.response.RegionalVocabularyResponse> variants;
        int variantPage;
        int variantSize;
        long variantTotalElements;
        int variantTotalPages;
        if (management) {
            variants =
                    adminRegional.forDestination(
                            vocabularyIds, destination.getRegion(), false);
            variantPage = 0;
            variantSize = variants.size();
            variantTotalElements = variants.size();
            variantTotalPages = variants.isEmpty() ? 0 : 1;
        } else {
            var page = regional.forDestinationPage(vocabularyIds, destination.getRegion(), regionalPage);
            variants = page.getContent();
            variantPage = page.getNumber();
            variantSize = page.getSize();
            variantTotalElements = page.getTotalElements();
            variantTotalPages = page.getTotalPages();
        }
        List<String> cultureIds = visibleCultureIds(references(id, "CULTURE"), management);
        return new DestinationExploreContentResponse(
                vocabulary,
                variants,
                variantPage,
                variantSize,
                variantTotalElements,
                variantTotalPages,
                cultureIds,
                scenarioRows);
    }

    /**
     * Giu thu tu lien ket da luu, bo tham chieu da bi xoa va chi cong khai bai van hoa da
     * xuat ban.
     */
    private List<String> visibleCultureIds(List<String> ids, boolean management) {
        if (ids.isEmpty()) return List.of();
        var available =
                cultureArticles.findAllById(ids).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        article -> article.getId(), article -> article));
        return ids.stream()
                .filter(
                        cultureId -> {
                            var article = available.get(cultureId);
                            return article != null
                                    && (management || "PUBLISHED".equals(article.getStatus()));
                        })
                .toList();
    }

    /** Kiem tra va luu cac lien ket theo dung thu tu trong request. */
    @Transactional
    public DestinationExploreContentResponse save(
            long id, DestinationExploreContentRequest request) {
        if (em.find(Destination.class, id, LockModeType.PESSIMISTIC_WRITE) == null)
            throw LearningException.notFound("Destination");
        unique(request.vocabularyIds());
        unique(request.cultureIds());
        unique(request.scenarioIds());
        var selected =
                words.findExistingWords(request.vocabularyIds()).stream()
                        .collect(java.util.stream.Collectors.toMap(VocabularyWord::getId, w -> w));
        if (selected.size() != request.vocabularyIds().size())
            throw LearningException.notFound("Vocabulary");
        if (scenarios.findAllById(request.scenarioIds()).size() != request.scenarioIds().size())
            throw LearningException.notFound("Scenario");
        var selectedCultures = cultureArticles.findAllById(request.cultureIds());
        if (selectedCultures.size() != request.cultureIds().size()
                || selectedCultures.stream()
                        .anyMatch(article -> !"PUBLISHED".equals(article.getStatus())))
            throw LearningException.notFound("Published culture article");
        var previous =
                links.findByDestinationIdOrderByDisplayOrderAscIdAsc(id).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        l -> l.getVocabulary().getId(), l -> l));
        links.deleteAll(
                previous.values().stream()
                        .filter(l -> !selected.containsKey(l.getVocabulary().getId()))
                        .toList());
        for (int i = 0; i < request.vocabularyIds().size(); i++) {
            long word = request.vocabularyIds().get(i);
            var link = previous.getOrDefault(word, new DestinationVocabulary());
            link.setDestinationId(id);
            link.setVocabulary(selected.get(word));
            link.setDisplayOrder(i);
            links.save(link);
        }
        links.flush();
        replace(id, "CULTURE", request.cultureIds());
        replace(id, "SCENARIO", request.scenarioIds());
        return content(id, true);
    }

    /** Doc cac ID lien ket theo thu tu hien thi da luu. */
    private List<String> references(long id, String type) {
        return jdbc.queryForList(
                "SELECT target_id FROM destination_explore_references WHERE destination_id=? AND"
                        + " content_type=? ORDER BY display_order,target_id",
                String.class,
                id,
                type);
    }

    /** Thay nhom lien ket cua dia diem va luu dung thu tu request. */
    private void replace(long id, String type, List<String> ids) {
        jdbc.update(
                "DELETE FROM destination_explore_references WHERE destination_id=? AND"
                        + " content_type=?",
                id,
                type);
        for (int i = 0; i < ids.size(); i++)
            jdbc.update(
                    "INSERT INTO"
                        + " destination_explore_references(destination_id,content_type,target_id,display_order)"
                        + " VALUES(?,?,?,?)",
                    id,
                    type,
                    ids.get(i),
                    i);
    }

    /** Chan request chua ID lien ket bi trung. */
    private static void unique(List<?> values) {
        if (new HashSet<>(values).size() != values.size())
            throw new LearningException(
                    LearningException.Kind.INVALID, "Không được liên kết trùng nội dung.");
    }
}
