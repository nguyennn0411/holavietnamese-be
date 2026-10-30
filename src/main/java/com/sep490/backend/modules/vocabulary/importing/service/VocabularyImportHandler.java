package com.sep490.backend.modules.vocabulary.importing.service;

import com.sep490.backend.modules.vocabulary.dto.request.VocabularyExampleRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyWordRequest;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;
import com.sep490.backend.modules.vocabulary.service.VocabularyWordService;
import jakarta.validation.Validator;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Repeated word/POS rows describe ordered meanings and examples in a single sheet. */
@Component @RequiredArgsConstructor
public class VocabularyImportHandler implements VocabularyImportHandlerContract {
    private final VocabularyWordService vocabulary;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private static final List<String> COLUMNS=List.of("word","part_of_speech","pronunciation","audio_url","status",
            "meaning_order","definition_vi","translation_en","definition_en","usage_note","labels",
            "example_order","example_vi","example_en","example_audio_url");
    public Type type() { return new Type("VOCABULARY","Từ vựng · Excel","VOCABULARY",COLUMNS,List.of("word","part_of_speech"),"xlsx",
            "Các dòng cùng word và part_of_speech được gom thành một mục từ. meaning_order phân biệt nghĩa; "
            + "example_order sắp xếp ví dụ, tính từ 0. Mỗi nghĩa cần thông tin nghĩa Việt hoặc Anh. "
            + "labels: mỗi nhãn một dòng trong ô. Trạng thái trống là DRAFT. Chỉ tạo mới, không ghi đè mục đã có."); }
    public List<Error> validate(List<Row> rows) {
        var errors=new ArrayList<Error>();
        for(var entry:assemble(rows,errors).values()) {
            var request=entry.request();
            if(!request.word().isBlank() && !request.partOfSpeech().isBlank() && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM vocabularies WHERE LOWER(word)=LOWER(?) AND LOWER(part_of_speech)=LOWER(?)",
                    Long.class,request.word(),request.partOfSpeech())>0)
                errors.add(error(entry.first,"word","Từ và từ loại đã tồn tại. Hãy sửa mục hiện có; Excel không ghi đè dữ liệu."));
            for(var violation:validator.validate(request))
                errors.add(error(entry.first,violation.getPropertyPath().toString(),violation.getMessage()));
        }
        return errors;
    }
    public int importCount(List<Row> rows) { return assemble(rows,new ArrayList<>()).size(); }
    public void write(List<Row> rows) {
        var errors=new ArrayList<Error>(); var entries=assemble(rows,errors);
        if(!errors.isEmpty()) throw new IllegalArgumentException("Invalid vocabulary workbook");
        for(var entry:entries.values()) vocabulary.create(entry.request());
    }
    private Map<String,Entry> assemble(List<Row> rows,List<Error> errors) {
        var entries=new LinkedHashMap<String,Entry>();
        var seenRows=new HashSet<Map<String,String>>();
        for(var row:rows) {
            if(!seenRows.add(row.values())) { errors.add(error(row,"word","Dòng Excel bị trùng hoàn toàn.")); continue; }
            String word=VocabularyText.normalize(value(row,"word")),part=value(row,"part_of_speech");
            String key=word.toLowerCase(Locale.ROOT)+"\u0000"+part.toLowerCase(Locale.ROOT);
            var entry=entries.computeIfAbsent(key,ignored -> new Entry(row,word,part));
            merge(entry.fields,row,List.of("pronunciation","audio_url","status"),errors);
            int order=order(row,"meaning_order",0,errors);
            var meaning=entry.meanings.computeIfAbsent(order,ignored -> new Meaning(order));
            merge(meaning.fields,row,List.of("definition_vi","translation_en","definition_en","usage_note","labels"),errors);
            if(!value(row,"example_vi").isBlank()) {
                int position=order(row,"example_order",meaning.examples.isEmpty()?0:meaning.examples.lastKey()+1,errors);
                var example=new VocabularyExampleRequest(value(row,"example_vi"),blankToNull(value(row,"example_en")),
                        blankToNull(value(row,"example_audio_url")),position);
                if(meaning.examples.putIfAbsent(position,example)!=null)
                    errors.add(error(row,"example_order","Thứ tự ví dụ bị trùng trong cùng nghĩa."));
            } else if(!value(row,"example_en").isBlank() || !value(row,"example_audio_url").isBlank())
                errors.add(error(row,"example_vi","Bản dịch hoặc audio ví dụ cần có câu ví dụ tiếng Việt."));
        }
        for(var entry:entries.values()) {
            String status=entry.fields.getOrDefault("status","");
            try {
                entry.status=status.isBlank()?VocabularyStatus.DRAFT:VocabularyStatus.valueOf(status);
                if (entry.status != VocabularyStatus.DRAFT)
                    errors.add(error(entry.first,"status","Dữ liệu import phải ở trạng thái DRAFT trước khi gửi duyệt."));
            }
            catch(IllegalArgumentException failure) { errors.add(error(entry.first,"status","Trạng thái import hợp lệ: DRAFT.")); }
        }
        return entries;
    }
    private void merge(Map<String,String> target,Row row,List<String> names,List<Error> errors) {
        for(String name:names) {
            String text=value(row,name); if(text.isBlank()) continue;
            String previous=target.putIfAbsent(name,text);
            if(previous!=null && !previous.equals(text)) errors.add(error(row,name,
                    "Nội dung không thống nhất trong cùng mục từ/nghĩa. Dùng meaning_order khác nếu đây là nghĩa khác."));
        }
    }
    private int order(Row row,String field,int fallback,List<Error> errors) {
        if(value(row,field).isBlank()) return fallback;
        try { int n=Integer.parseInt(value(row,field)); if(n<0 || n>20000) throw new NumberFormatException(); return n; }
        catch(NumberFormatException failure) { errors.add(error(row,field,"Thứ tự phải là số nguyên từ 0 đến 20.000.")); return fallback; }
    }
    private Error error(Row row,String column,String message) { return new Error(type().sheet(),row.rowNumber(),column,message); }
    private static String value(Row row,String name) { return row.values().getOrDefault(name,"").strip(); }
    private static String blankToNull(String text) { return text==null || text.isBlank()?null:text; }
    private static class Entry {
        final Row first; final String word,part;
        final Map<String,String> fields=new HashMap<>(); final SortedMap<Integer,Meaning> meanings=new TreeMap<>();
        VocabularyStatus status=VocabularyStatus.DRAFT;
        Entry(Row first,String word,String part) { this.first=first; this.word=word; this.part=part; }
        VocabularyWordRequest request() { return new VocabularyWordRequest(word,fields.get("pronunciation"),part,fields.get("audio_url"),status,
                meanings.values().stream().map(Meaning::request).toList()); }
    }
    private static class Meaning {
        final int order; final Map<String,String> fields=new HashMap<>();
        final SortedMap<Integer,VocabularyExampleRequest> examples=new TreeMap<>();
        Meaning(int order) { this.order=order; }
        VocabularyMeaningRequest request() { return new VocabularyMeaningRequest(fields.get("translation_en"),fields.get("definition_en"),fields.get("usage_note"),
                order,List.copyOf(examples.values()),fields.get("definition_vi"),
                fields.getOrDefault("labels","").lines().map(String::strip).filter(s -> !s.isBlank()).distinct().toList()); }
    }
}
