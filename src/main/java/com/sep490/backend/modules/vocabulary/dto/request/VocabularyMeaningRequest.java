package com.sep490.backend.modules.vocabulary.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VocabularyMeaningRequest(

        @Size(max = 500)
        String translationEn,

        String definitionEn,

        String usageNote,

        @PositiveOrZero
        Integer displayOrder,

        @Valid
        List<@jakarta.validation.constraints.NotNull VocabularyExampleRequest> examples,

        @Size(max = 10000) String definitionVi,
        @Size(max = 100) List<@NotBlank @Size(max = 200) String> labels
) {
    public VocabularyMeaningRequest(String translationEn,String definitionEn,String usageNote,Integer displayOrder,List<VocabularyExampleRequest> examples) {
        this(translationEn,definitionEn,usageNote,displayOrder,examples,null,List.of());
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message="Cần định nghĩa tiếng Việt hoặc nghĩa tiếng Anh.")
    public boolean isContentPresent() {
        return (translationEn != null && !translationEn.isBlank()) || (definitionVi != null && !definitionVi.isBlank())
                || (definitionEn != null && !definitionEn.isBlank());
    }
}
