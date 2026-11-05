package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Row;
import com.sep490.backend.modules.vocabulary.importing.service.RegionalVariantImportPort;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyImportHandler;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyWordJpaRepository;
import com.sep490.backend.modules.vocabulary.service.VocabularyWordService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class VocabularyImportHandlerTests {
    private static final ValidatorFactory VALIDATOR_FACTORY=Validation.buildDefaultValidatorFactory();

    @AfterAll static void closeValidator() { VALIDATOR_FACTORY.close(); }

    @Test void validatesRegionalFieldsBeforeDatabaseWrites() {
        var handler=handler(mock(VocabularyWordJpaRepository.class),mock(RegionalVariantImportPort.class),mock(VocabularyWordService.class));
        var values=base("xin chào");
        values.put("region","WEST"); values.put("regional_word",""); values.put("regional_meaning","");
        values.put("regional_example_en","Hello from a region");

        var errors=handler.validate(List.of(new Row(2,values)));

        assertThat(errors).extracting(error -> error.column()).contains("region","regional_word","regional_meaning","regional_example_vi");
    }

    @Test void existingMasterAndVariantAreReportedAndSkippedWithoutWrites() {
        var words=mock(VocabularyWordJpaRepository.class); var variants=mock(RegionalVariantImportPort.class);
        var service=mock(VocabularyWordService.class); var existing=new VocabularyWordJpaEntity();
        existing.setId(41L); existing.setWord("quả dứa"); existing.setPartOfSpeech("noun");
        when(words.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase("quả dứa","noun")).thenReturn(List.of(existing));
        when(variants.exists(eq(41L),any())).thenReturn(true);
        var handler=handler(words,variants,service); var values=base("quả dứa");
        values.put("region","SOUTH"); values.put("regional_word","thơm"); values.put("regional_meaning","Quả dứa");

        var preview=handler.previewSummary(List.of(new Row(2,values)));
        var result=handler.write(List.of(new Row(2,values)));

        assertThat((List<?>)preview.get("duplicateWords")).hasSize(1);
        assertThat((List<?>)preview.get("duplicateRegionalVariants")).hasSize(1);
        assertThat(result.importedRows()).isZero();
        assertThat((List<?>)result.summary().get("duplicateWords")).hasSize(1);
        verifyNoInteractions(service);
        verify(variants,never()).createDraft(anyLong(),any());
    }

    @Test void invalidRegionNeverReachesExploreAdapterDuringPreview() {
        var words=mock(VocabularyWordJpaRepository.class); var variants=mock(RegionalVariantImportPort.class);
        var existing=new VocabularyWordJpaEntity(); existing.setId(12L); existing.setWord("dứa"); existing.setPartOfSpeech("noun");
        when(words.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase("dứa","noun")).thenReturn(List.of(existing));
        var handler=handler(words,variants,mock(VocabularyWordService.class)); var values=base("dứa");
        values.put("region","WEST"); values.put("regional_word","thơm"); values.put("regional_meaning","Quả dứa");

        assertThat(handler.validate(List.of(new Row(2,values)))).anyMatch(error -> error.column().equals("region"));
        handler.previewSummary(List.of(new Row(2,values)));

        verifyNoInteractions(variants);
    }

    @Test void accentSensitiveIdentityDoesNotTreatDifferentVietnameseWordsAsDuplicates() {
        var words=mock(VocabularyWordJpaRepository.class); var existing=new VocabularyWordJpaEntity();
        existing.setId(9L); existing.setWord("ma"); existing.setPartOfSpeech("noun");
        when(words.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase("má","noun")).thenReturn(List.of(existing));
        var handler=handler(words,mock(RegionalVariantImportPort.class),mock(VocabularyWordService.class));

        var preview=handler.previewSummary(List.of(new Row(2,base("má"))));

        assertThat((List<?>)preview.get("newWords")).hasSize(1);
        assertThat((List<?>)preview.get("duplicateWords")).isEmpty();
    }

    private static VocabularyImportHandler handler(VocabularyWordJpaRepository words,RegionalVariantImportPort variants,VocabularyWordService service) {
        return new VocabularyImportHandler(service,words,variants,VALIDATOR_FACTORY.getValidator());
    }
    private static Map<String,String> base(String word) {
        var values=new LinkedHashMap<String,String>();
        values.put("word",word); values.put("part_of_speech","noun"); values.put("definition_vi","Nghĩa kiểm thử");
        return values;
    }
}
