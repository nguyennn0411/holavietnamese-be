package com.sep490.backend.modules.explore.regional.service;

import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.regional.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.regional.repository.jpa.RegionalVocabularyJpaRepository;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.importing.service.RegionalVariantImportPort;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionalVariantImportAdapter implements RegionalVariantImportPort {
    private final RegionalVocabularyJpaRepository repository;
    private final RegionalVocabularyService service;

    @Override
    public boolean exists(long vocabularyId, Variant variant) {
        VietnamRegion region = VietnamRegion.valueOf(variant.region().toUpperCase(Locale.ROOT));
        String normalized = VocabularyText.normalize(variant.word());
        return repository.findByVocabularyIdAndRegion(vocabularyId, region).stream()
                .anyMatch(row -> VocabularyText.normalize(row.getWord()).equalsIgnoreCase(normalized));
    }

    @Override
    public void createDraft(long vocabularyId, Variant variant) {
        service.createDraft(new RegionalVocabularyRequest(vocabularyId,
                VietnamRegion.valueOf(variant.region().toUpperCase(Locale.ROOT)),
                variant.word(), variant.meaning(), variant.meaningEn(), variant.pronunciation(),
                variant.audioUrl(), variant.exampleSentence(), variant.exampleSentenceEn(),
                variant.usageNote(), variant.usageNoteEn(), VocabularyStatus.DRAFT));
    }
}
