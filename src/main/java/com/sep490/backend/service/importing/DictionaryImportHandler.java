package com.sep490.backend.service.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.dto.response.ContentImportResponse.*;
import com.sep490.backend.dto.response.ContentImportResponse.Error;
import com.sep490.backend.repository.jpa.VocabularyWordJpaRepository;
import com.sep490.backend.service.VocabularyWordService;
import jakarta.validation.Validator;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class DictionaryImportHandler implements ContentImportHandler {
    private final VocabularyWordService vocabulary;
    private final VocabularyWordJpaRepository words;
    private final Validator validator;
    private final ObjectMapper json;
    public Type type() { return new Type("DICTIONARY_JSONL","Từ điển Wiktionary · JSONL","JSONL",
            List.of("word","part_of_speech","senses","definition_vi","translation_en","status","action"),List.of(),"jsonl",
            "Pilot từ Wiktionary tiếng Việt/Kaikki, CC BY-SA 4.0. Chỉ nhận file có checksum được đăng ký. Mọi mục mới ở PENDING_REVIEW; không ghi đè từ đã có, không tự gán CEFR."); }
    public String lockModule() { return "VOCABULARY"; }
    public List<Error> validate(List<Row> rows) {
        var errors=new ArrayList<Error>();
        for(var row:rows) {
            var entry=entry(row); var request=entry.request();
            for(var violation:validator.validate(request)) errors.add(new Error("JSONL",row.rowNumber(),violation.getPropertyPath().toString(),violation.getMessage()));
            // Original record identity survives editorial corrections to the headword/POS.
            boolean exists=words.existsBySourceMetadataContaining("\"entrySha256\":\""+entry.source().entrySha256()+"\"")
                    || words.existsByWordIgnoreCaseAndPartOfSpeechIgnoreCaseAndIdNot(request.word(),request.partOfSpeech(),-1L);
            row.values().put("action",exists ? "SKIP_EXISTING" : "CREATE");
        }
        return errors;
    }
    public int importCount(List<Row> rows) { return (int)rows.stream().filter(r -> "CREATE".equals(r.values().get("action"))).count(); }
    public void write(List<Row> rows) {
        for(var row:rows) if("CREATE".equals(row.values().get("action"))) {
            var entry=entry(row); vocabulary.createDictionary(entry.request(),entry.source(),entry.lexical(),entry.raw());
        }
    }
    private DictionaryJsonlReader.Entry entry(Row row) {
        try { return json.readValue(row.values().get("_dictionary"),DictionaryJsonlReader.Entry.class); }
        catch(Exception failure) { throw new IllegalStateException("Cannot read server-owned dictionary preview",failure); }
    }
}
