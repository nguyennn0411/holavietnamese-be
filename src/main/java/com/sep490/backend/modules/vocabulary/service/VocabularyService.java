package com.sep490.backend.modules.vocabulary.service;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyResponse;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyMapper;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyService {
    private final VocabularyRepository repository;

    /** Chi tra du lieu da xuat ban cho nguoi hoc. */
    public List<VocabularyResponse> searchPublished(String keyword, String partOfSpeech) {
        return repository.search(keyword, VocabularyStatus.PUBLISHED, partOfSpeech).stream()
                .map(VocabularyMapper::toResponse)
                .toList();
    }

    /** Tra chi tiet da xuat ban, bao loi neu noi dung chua duoc cong khai. */
    public VocabularyResponse findPublishedById(Long id) {
        VocabularyResponse word =
                repository
                        .findById(id)
                        .map(VocabularyMapper::toResponse)
                        .filter(item -> item.status() == VocabularyStatus.PUBLISHED)
                        .orElseThrow(() -> LearningException.notFound("Vocabulary"));

        return word;
    }

    /** Truy van du lieu theo bo loc va gioi han kich thuoc trang. */
    public org.springframework.data.domain.Page<VocabularyResponse> searchPage(
            String keyword, int page, int size, boolean exact) {

        if (keyword != null && keyword.length() > 200) {
            throw new LearningException(
                    LearningException.Kind.INVALID, "Từ khóa tối đa 200 ký tự.");
        }

        return repository
                .searchPage(keyword, VocabularyStatus.PUBLISHED, null, page, size, exact)
                .map(VocabularyMapper::toResponse);
    }

    /** Lay cac tu cung cach viet va cac tu lien quan da xuat ban. */
    public java.util.Map<String, Object> links(Long id) {
        var word =
                repository
                        .findById(id)
                        .map(VocabularyMapper::toResponse)
                        .orElseThrow(() -> LearningException.notFound("Vocabulary"));

        if (word.status() != VocabularyStatus.PUBLISHED) {
            throw LearningException.notFound("Vocabulary");
        }

        var family =
                repository
                        .findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(
                                word.word(), VocabularyStatus.PUBLISHED)
                        .stream()
                        .filter(w -> !w.getId().equals(id))
                        .map(
                                w ->
                                        java.util.Map.of(
                                                "id", w.getId(),
                                                "word", w.getWord(),
                                                "partOfSpeech", w.getPartOfSpeech()))
                        .toList();

        var related = new java.util.ArrayList<java.util.Map<String, Object>>();

        if (word.lexical() != null && word.lexical().relatedWords() != null) {
            for (var relation : word.lexical().relatedWords()) {
                var row = new java.util.LinkedHashMap<String, Object>();

                row.put("word", relation.word());
                row.put("kind", relation.kind());
                row.put("sense", relation.sense());

                var targets =
                        repository.findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(
                                relation.word(), VocabularyStatus.PUBLISHED);

                row.put("id", targets.isEmpty() ? null : targets.getFirst().getId());
                related.add(row);
            }
        }

        return java.util.Map.of(
                "otherPartsOfSpeech", family,
                "relatedWords", related);
    }
}
