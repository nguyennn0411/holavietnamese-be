package com.sep490.backend.modules.explore.service;

import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.RegionalVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.importing.service.RegionalVariantImportPort;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RegionalVariantImportAdapter implements RegionalVariantImportPort {
    private final RegionalVocabularyRepository repository;
    private final AdminRegionalVocabularyService service;

    /** Kiem tra bien the vung mien da ton tai cho tu chuan hay chua. */
    @Override
    public boolean exists(long vocabularyId, Variant variant) {
        VietnamRegion region = VietnamRegion.valueOf(variant.region().toUpperCase(Locale.ROOT));
        String normalized = VocabularyText.normalize(variant.word());
        return repository.findByVocabularyIdAndRegion(vocabularyId, region).stream()
                .anyMatch(
                        row ->
                                VocabularyText.normalize(row.getWord())
                                        .equalsIgnoreCase(normalized));
    }

    /** Tao ban nhap va chan viec tu gan trang thai xuat ban. */
    @Override
    public void createDraft(long vocabularyId, Variant variant) {
        service.createDraft(
                new RegionalVocabularyRequest(
                        vocabularyId,
                        VietnamRegion.valueOf(variant.region().toUpperCase(Locale.ROOT)),
                        variant.word(),
                        variant.meaning(),
                        variant.meaningEn(),
                        variant.pronunciation(),
                        variant.audioUrl(),
                        variant.exampleSentence(),
                        variant.exampleSentenceEn(),
                        variant.usageNote(),
                        variant.usageNoteEn(),
                        VocabularyStatus.DRAFT));
    }
}
