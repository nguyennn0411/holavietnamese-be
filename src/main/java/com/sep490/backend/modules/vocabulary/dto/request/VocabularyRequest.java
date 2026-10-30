
package com.sep490.backend.modules.vocabulary.dto.request;

import jakarta.validation.constraints.*;

public record VocabularyRequest(
        @Positive Long lessonId,
        @NotBlank @Size(max = 200) String word,
        @NotBlank @Size(max = 1000) String meaning,
        @Size(max = 200) String pronunciation,
        @Size(max = 2000) String exampleSentence,
        @Size(max = 2000) String note,
        @Positive Long masterVocabularyId
) {
    public VocabularyRequest(
            Long lessonId,
            String word,
            String meaning,
            String pronunciation,
            String exampleSentence,
            String note
    ) {
        this(lessonId, word, meaning, pronunciation,
                exampleSentence, note, null);
    }
}
