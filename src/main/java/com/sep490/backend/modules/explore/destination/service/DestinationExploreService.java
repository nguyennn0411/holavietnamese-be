package com.sep490.backend.modules.explore.destination.service;

import com.sep490.backend.modules.explore.destination.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.destination.dto.response.DestinationExploreContentResponse;
import com.sep490.backend.modules.explore.regional.service.RegionalVocabularyService;
import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.explore.destination.entity.DestinationJpaEntity;
import com.sep490.backend.modules.explore.destination.entity.DestinationVocabularyJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;
import com.sep490.backend.modules.explore.destination.entity.enums.DestinationStatus;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.ai.repository.AiScenarioRepository;
import com.sep490.backend.modules.culture.repository.CultureArticleRepository;
import com.sep490.backend.modules.explore.destination.repository.jpa.DestinationJpaRepository;
import com.sep490.backend.modules.explore.destination.repository.jpa.DestinationVocabularyJpaRepository;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyWordJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only exploration has no course prerequisites, progress or rewards. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DestinationExploreService {
    private final DestinationJpaRepository destinations;
    private final DestinationVocabularyJpaRepository links;
    private final VocabularyWordJpaRepository words;
    private final AiScenarioRepository scenarios;
    private final CultureArticleRepository cultureArticles;
    private final RegionalVocabularyService regional;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public DestinationExploreContentResponse content(long id, boolean management) {
        var destination = destinations.findById(id).orElseThrow(() -> LearningException.notFound("Destination"));
        if (!management && destination.getStatus() != DestinationStatus.PUBLISHED)
            throw LearningException.notFound("Destination");
        var vocabulary = links.findByDestinationIdOrderByDisplayOrderAscIdAsc(id).stream()
                .map(DestinationVocabularyJpaEntity::getVocabulary)
                .filter(w -> management || w.getStatus() == VocabularyStatus.PUBLISHED)
                .map(w -> new DestinationExploreContentResponse.Word(w.getId(),w.getWord(),w.getPronunciation(),w.getStatus().name())).toList();
        var scenarioIds = references(id,"SCENARIO");
        var available = scenarios.findAllById(scenarioIds).stream().collect(java.util.stream.Collectors.toMap(AiScenario::getId,s -> s));
        var scenarioRows = scenarioIds.stream().map(available::get).filter(Objects::nonNull)
                .filter(s -> management || "ACTIVE".equals(s.getStatus()))
                .map(s -> new DestinationExploreContentResponse.Scenario(s.getId(),s.getTitle(),s.getLevel(),s.getOverview(),s.getThumbnail(),s.getStatus())).toList();
        return new DestinationExploreContentResponse(vocabulary,
                regional.forDestination(vocabulary.stream().map(DestinationExploreContentResponse.Word::id).toList(),destination.getRegion(),!management),
                references(id,"CULTURE"),scenarioRows);
    }

    @Transactional
    public DestinationExploreContentResponse save(long id, DestinationExploreContentRequest request) {
        if (em.find(DestinationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE) == null)
            throw LearningException.notFound("Destination");
        unique(request.vocabularyIds()); unique(request.cultureIds()); unique(request.scenarioIds());
        var selected = words.findAllById(request.vocabularyIds()).stream()
                .collect(java.util.stream.Collectors.toMap(VocabularyWordJpaEntity::getId,w -> w));
        if (selected.size() != request.vocabularyIds().size()) throw LearningException.notFound("Vocabulary");
        if (scenarios.findAllById(request.scenarioIds()).size() != request.scenarioIds().size())
            throw LearningException.notFound("Scenario");
        var selectedCultures = cultureArticles.findAllById(request.cultureIds());
        if (selectedCultures.size() != request.cultureIds().size()
                || selectedCultures.stream().anyMatch(article -> !"PUBLISHED".equals(article.getStatus())))
            throw LearningException.notFound("Published culture article");
        var previous = links.findByDestinationIdOrderByDisplayOrderAscIdAsc(id).stream()
                .collect(java.util.stream.Collectors.toMap(l -> l.getVocabulary().getId(),l -> l));
        links.deleteAll(previous.values().stream().filter(l -> !selected.containsKey(l.getVocabulary().getId())).toList());
        for (int i=0;i<request.vocabularyIds().size();i++) {
            long word = request.vocabularyIds().get(i);
            var link = previous.getOrDefault(word,new DestinationVocabularyJpaEntity());
            link.setDestinationId(id); link.setVocabulary(selected.get(word)); link.setDisplayOrder(i);
            links.save(link);
        }
        links.flush();
        replace(id,"CULTURE",request.cultureIds()); replace(id,"SCENARIO",request.scenarioIds());
        return content(id,true);
    }
    private List<String> references(long id,String type) {
        return jdbc.queryForList("SELECT target_id FROM destination_explore_references WHERE destination_id=? AND content_type=? ORDER BY display_order,target_id",String.class,id,type);
    }
    private void replace(long id,String type,List<String> ids) {
        jdbc.update("DELETE FROM destination_explore_references WHERE destination_id=? AND content_type=?",id,type);
        for (int i=0;i<ids.size();i++) jdbc.update("INSERT INTO destination_explore_references(destination_id,content_type,target_id,display_order) VALUES(?,?,?,?)",id,type,ids.get(i),i);
    }
    private static void unique(List<?> values) {
        if (new HashSet<>(values).size()!=values.size())
            throw new LearningException(LearningException.Kind.INVALID,"Không được liên kết trùng nội dung.");
    }
}
