package com.sep490.backend.service.importing;

import com.sep490.backend.dto.request.*;
import com.sep490.backend.dto.response.ContentImportResponse.*;
import com.sep490.backend.dto.response.ContentImportResponse.Error;
import com.sep490.backend.entity.enums.VocabularyStatus;
import com.sep490.backend.repository.jpa.VocabularyTopicJpaRepository;
import com.sep490.backend.service.VocabularyWordService;
import jakarta.validation.Validator;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class VocabularyImportHandler implements ContentImportHandler {
    private final VocabularyWordService vocabulary;
    private final VocabularyTopicJpaRepository topics;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private static final List<String> COLUMNS = List.of("word","part_of_speech","cefr_level","translation_en",
            "pronunciation","audio_url","status","definition_en","usage_note","example_vi","example_en","example_audio_url","topic_ids");
    public Type type() { return new Type("VOCABULARY","Từ vựng chuẩn","VOCABULARY",COLUMNS,COLUMNS.subList(0,4)); }
    public List<Error> validate(List<Row> rows) {
        var errors = new ArrayList<Error>(); var seen = new HashSet<String>();
        for (var row : rows) {
            var values = row.values();
            var word = values.getOrDefault("word",""); var part = values.getOrDefault("part_of_speech","");
            String key = (word + "\u0000" + part).toLowerCase(Locale.ROOT);
            if (!seen.add(key)) errors.add(error(row,"word","Trùng từ và từ loại trong workbook."));
            if (!word.isBlank() && !part.isBlank() && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vocabularies WHERE LOWER(word)=LOWER(?) AND LOWER(part_of_speech)=LOWER(?)",Long.class,word,part) > 0)
                errors.add(error(row,"word","Từ và từ loại đã tồn tại. Import này chỉ tạo mới, không ghi đè từ đã có."));
            if (!Set.of("A1","A2","B1","B2","C1","C2").contains(values.getOrDefault("cefr_level","")))
                errors.add(error(row,"cefr_level","Chọn trình độ A1, A2, B1, B2, C1 hoặc C2."));
            for (String name : List.of("definition_en","usage_note","example_vi","example_en"))
                if (values.getOrDefault(name,"").length() > 10000) errors.add(error(row,name,"Tối đa 10.000 ký tự theo giới hạn Excel hiện có."));
            if (values.getOrDefault("example_vi","").isBlank() != values.getOrDefault("example_en","").isBlank())
                errors.add(error(row,"example_vi","Ví dụ cần cả tiếng Việt và bản dịch tiếng Anh."));
            if (!values.getOrDefault("example_audio_url","").isBlank() && values.getOrDefault("example_vi","").isBlank())
                errors.add(error(row,"example_audio_url","Audio ví dụ cần có câu ví dụ."));
            VocabularyWordRequest request;
            try { request = request(row); }
            catch (IllegalArgumentException failure) { errors.add(error(row,"status/topic_ids",failure.getMessage())); continue; }
            for (var violation : validator.validate(request)) errors.add(error(row,column(violation.getPropertyPath().toString()),violation.getMessage()));
            for (Long id : request.topicIds()) if (!topics.existsById(id)) errors.add(error(row,"topic_ids","Không tìm thấy chủ đề ID " + id));
        }
        return errors;
    }
    public void write(List<Row> rows) { for (var row : rows) vocabulary.create(request(row)); }
    private VocabularyWordRequest request(Row row) {
        var v = row.values(); var ids = new LinkedHashSet<Long>();
        var rawIds = v.getOrDefault("topic_ids","");
        if (!rawIds.isBlank()) for (String raw : rawIds.split(",",-1)) {
            try { long id = Long.parseLong(raw.strip()); if (id <= 0 || !ids.add(id)) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("topic_ids phải là các ID dương, không trùng, phân cách bằng dấu phẩy."); }
        }
        VocabularyStatus status;
        try { status = v.getOrDefault("status","").isBlank() ? VocabularyStatus.DRAFT : VocabularyStatus.valueOf(v.get("status")); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Trạng thái không hợp lệ: DRAFT, PENDING_REVIEW, PUBLISHED hoặc ARCHIVED."); }
        var examples = v.getOrDefault("example_vi","").isBlank() ? List.<VocabularyExampleRequest>of() : List.of(
                new VocabularyExampleRequest(v.get("example_vi"),v.get("example_en"),v.get("example_audio_url"),0));
        return new VocabularyWordRequest(v.get("word"),v.get("pronunciation"),v.get("part_of_speech"),v.get("cefr_level"),v.get("audio_url"),status,ids,
                List.of(new VocabularyMeaningRequest(v.get("translation_en"),v.get("definition_en"),v.get("usage_note"),0,examples)));
    }
    private Error error(Row row,String column,String message) { return new Error(type().sheet(),row.rowNumber(),column,message); }
    private static String column(String path) {
        return switch(path) {
            case "meanings[0].translationEn" -> "translation_en";
            case "meanings[0].examples[0].exampleVi" -> "example_vi";
            case "meanings[0].examples[0].translationEn" -> "example_en";
            case "meanings[0].examples[0].audioUrl" -> "example_audio_url";
            case "partOfSpeech" -> "part_of_speech";
            case "cefrLevel" -> "cefr_level";
            case "audioUrl" -> "audio_url";
            default -> path;
        };
    }
}
