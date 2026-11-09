package com.sep490.backend.modules.vocabulary.importing.service;

import com.sep490.backend.modules.vocabulary.dto.request.VocabularyExampleRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyRequest;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

/** Cac dong cung tu va tu loai duoc gom thanh cac nghia va vi du co thu tu. */
@Component
@RequiredArgsConstructor
public class VocabularyImportHandler {
    public record WriteResult(int importedRows, Map<String, Object> summary) {
        public WriteResult {
            summary = summary == null ? Map.of() : Map.copyOf(summary);
        }
    }

    /** Xac dinh module can khoa khi xac nhan import. */
    public String lockModule() {
        return type().id();
    }

    private final AdminVocabularyService vocabulary;
    private final VocabularyRepository words;
    private final RegionalVariantImportPort regionalVariants;
    private final Validator validator;
    private static final List<String> COLUMNS =
            List.of(
                    "word",
                    "part_of_speech",
                    "pronunciation",
                    "audio_url",
                    "status",
                    "meaning_order",
                    "definition_vi",
                    "translation_en",
                    "definition_en",
                    "usage_note",
                    "labels",
                    "example_order",
                    "example_vi",
                    "example_en",
                    "example_audio_url",
                    "region",
                    "regional_word",
                    "regional_meaning",
                    "regional_meaning_en",
                    "regional_pronunciation",
                    "regional_audio_url",
                    "regional_example_vi",
                    "regional_example_en",
                    "regional_usage_note",
                    "regional_usage_note_en");
    private static final List<String> REGIONAL_COLUMNS =
            List.of(
                    "region",
                    "regional_word",
                    "regional_meaning",
                    "regional_meaning_en",
                    "regional_pronunciation",
                    "regional_audio_url",
                    "regional_example_vi",
                    "regional_example_en",
                    "regional_usage_note",
                    "regional_usage_note_en");

    /** Lay cau truc cot va quy tac cua loai import duoc ho tro. */
    public Type type() {
        return new Type(
                "VOCABULARY",
                "Từ vựng · Excel",
                "VOCABULARY",
                COLUMNS,
                List.of("word", "part_of_speech"),
                "xlsx",
                "Các dòng cùng word và part_of_speech được gom thành một mục từ. meaning_order phân"
                    + " biệt nghĩa; example_order sắp xếp ví dụ, tính từ 0. Mỗi nghĩa cần thông tin"
                    + " nghĩa Việt hoặc Anh. labels: mỗi nhãn một dòng trong ô. Nhóm regional_* tạo"
                    + " biến thể NORTH/CENTRAL/SOUTH. Trạng thái trống là DRAFT. Mục đã có được"
                    + " liệt kê và bỏ qua, không ghi đè.");
    }

    /** Kiem tra du lieu tung dong truoc khi cho phep luu. */
    public List<Error> validate(List<Row> rows) {
        var errors = new ArrayList<Error>();
        for (var entry : assemble(rows, errors).values()) {
            var request = entry.request();
            for (var violation : validator.validate(request))
                errors.add(
                        error(
                                entry.first,
                                violation.getPropertyPath().toString(),
                                violation.getMessage()));
        }
        return errors;
    }

    /** Tong hop cac muc moi va cac muc trung de hien thi ban xem truoc. */
    public Map<String, Object> previewSummary(List<Row> rows) {
        var entries = assemble(rows, new ArrayList<>());
        var newWords = new ArrayList<Map<String, Object>>();
        var duplicateWords = new ArrayList<Map<String, Object>>();
        var newVariants = new ArrayList<Map<String, Object>>();
        var duplicateVariants = new ArrayList<Map<String, Object>>();
        for (var entry : entries.values()) {
            var existing = findExisting(entry);
            (existing.isPresent() ? duplicateWords : newWords).add(wordItem(entry));
            for (var variant : entry.variants.values()) {
                boolean duplicate =
                        existing.isPresent()
                                && regionalVariants.exists(existing.get().getId(), variant.value());
                (duplicate ? duplicateVariants : newVariants).add(variantItem(entry, variant));
            }
        }
        return summary(
                "newWords",
                newWords,
                "duplicateWords",
                duplicateWords,
                "newRegionalVariants",
                newVariants,
                "duplicateRegionalVariants",
                duplicateVariants);
    }

    /** Luu tu chuan va bien the hop le, bo qua cac muc da ton tai. */
    public WriteResult write(List<Row> rows) {
        var errors = new ArrayList<Error>();
        var entries = assemble(rows, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid vocabulary workbook");
        var createdWords = new ArrayList<Map<String, Object>>();
        var duplicateWords = new ArrayList<Map<String, Object>>();
        var createdVariants = new ArrayList<Map<String, Object>>();
        var duplicateVariants = new ArrayList<Map<String, Object>>();
        for (var entry : entries.values()) {
            var existing = findExisting(entry);
            long vocabularyId;
            if (existing.isPresent()) {
                vocabularyId = existing.get().getId();
                duplicateWords.add(wordItem(entry));
            } else {
                vocabularyId = vocabulary.create(entry.request()).id();
                createdWords.add(wordItem(entry));
            }
            for (var variant : entry.variants.values()) {
                if (regionalVariants.exists(vocabularyId, variant.value()))
                    duplicateVariants.add(variantItem(entry, variant));
                else {
                    regionalVariants.createDraft(vocabularyId, variant.value());
                    createdVariants.add(variantItem(entry, variant));
                }
            }
        }
        var result =
                summary(
                        "createdWords",
                        createdWords,
                        "duplicateWords",
                        duplicateWords,
                        "createdRegionalVariants",
                        createdVariants,
                        "duplicateRegionalVariants",
                        duplicateVariants);
        return new WriteResult(createdWords.size() + createdVariants.size(), result);
    }

    /** Gom cac dong cung tu va tu loai thanh nghia va vi du co thu tu. */
    private Map<String, Entry> assemble(List<Row> rows, List<Error> errors) {
        var entries = new LinkedHashMap<String, Entry>();
        var seenRows = new HashSet<Map<String, String>>();
        for (var row : rows) {
            if (!seenRows.add(row.values())) {
                errors.add(error(row, "word", "Dòng Excel bị trùng hoàn toàn."));
                continue;
            }
            String word = VocabularyText.normalize(value(row, "word")),
                    part = value(row, "part_of_speech");
            String key = word.toLowerCase(Locale.ROOT) + "\u0000" + part.toLowerCase(Locale.ROOT);
            var entry = entries.computeIfAbsent(key, ignored -> new Entry(row, word, part));
            merge(entry.fields, row, List.of("pronunciation", "audio_url", "status"), errors);
            int order = order(row, "meaning_order", 0, errors);
            var meaning = entry.meanings.computeIfAbsent(order, ignored -> new Meaning(order));
            merge(
                    meaning.fields,
                    row,
                    List.of(
                            "definition_vi",
                            "translation_en",
                            "definition_en",
                            "usage_note",
                            "labels"),
                    errors);
            if (!value(row, "example_vi").isBlank()) {
                int position =
                        order(
                                row,
                                "example_order",
                                meaning.examples.isEmpty() ? 0 : meaning.examples.lastKey() + 1,
                                errors);
                var example =
                        new VocabularyExampleRequest(
                                value(row, "example_vi"),
                                blankToNull(value(row, "example_en")),
                                blankToNull(value(row, "example_audio_url")),
                                position);
                if (meaning.examples.putIfAbsent(position, example) != null)
                    errors.add(
                            error(row, "example_order", "Thứ tự ví dụ bị trùng trong cùng nghĩa."));
            } else if (!value(row, "example_en").isBlank()
                    || !value(row, "example_audio_url").isBlank())
                errors.add(
                        error(
                                row,
                                "example_vi",
                                "Bản dịch hoặc audio ví dụ cần có câu ví dụ tiếng Việt."));
            assembleRegional(entry, row, errors);
        }
        for (var entry : entries.values()) {
            String status = entry.fields.getOrDefault("status", "");
            try {
                entry.status =
                        status.isBlank()
                                ? VocabularyStatus.DRAFT
                                : VocabularyStatus.valueOf(status);
                if (entry.status != VocabularyStatus.DRAFT)
                    errors.add(
                            error(
                                    entry.first,
                                    "status",
                                    "Dữ liệu import phải ở trạng thái DRAFT trước khi gửi duyệt."));
            } catch (IllegalArgumentException failure) {
                errors.add(error(entry.first, "status", "Trạng thái import hợp lệ: DRAFT."));
            }
        }
        return entries;
    }

    /** Gom va kiem tra cac cot bien the vung mien trong tung dong. */
    private void assembleRegional(Entry entry, Row row, List<Error> errors) {
        if (REGIONAL_COLUMNS.stream().noneMatch(name -> !value(row, name).isBlank())) return;
        String region = value(row, "region").toUpperCase(Locale.ROOT);
        String word = VocabularyText.normalize(value(row, "regional_word"));
        String meaning = value(row, "regional_meaning");
        boolean validRegion = Set.of("NORTH", "CENTRAL", "SOUTH").contains(region);
        if (region.isBlank())
            errors.add(error(row, "region", "Vùng miền là bắt buộc khi có dữ liệu regional_*."));
        else if (!validRegion)
            errors.add(error(row, "region", "Vùng miền hợp lệ: NORTH, CENTRAL hoặc SOUTH."));
        if (word.isBlank())
            errors.add(error(row, "regional_word", "Biến thể vùng miền không được để trống."));
        if (meaning.isBlank())
            errors.add(
                    error(
                            row,
                            "regional_meaning",
                            "Nghĩa tiếng Việt của biến thể không được để trống."));
        length(row, "regional_word", word, 200, errors);
        length(row, "regional_meaning", meaning, 1000, errors);
        length(row, "regional_meaning_en", value(row, "regional_meaning_en"), 1000, errors);
        length(row, "regional_pronunciation", value(row, "regional_pronunciation"), 200, errors);
        length(row, "regional_audio_url", value(row, "regional_audio_url"), 2048, errors);
        for (String name :
                List.of(
                        "regional_example_vi",
                        "regional_example_en",
                        "regional_usage_note",
                        "regional_usage_note_en"))
            length(row, name, value(row, name), 2000, errors);
        if (value(row, "regional_example_vi").isBlank()
                && !value(row, "regional_example_en").isBlank())
            errors.add(
                    error(
                            row,
                            "regional_example_vi",
                            "Bản dịch ví dụ vùng miền cần có câu ví dụ tiếng Việt."));
        if (!validRegion || word.isBlank()) return;
        String key = region + "\u0000" + word.toLowerCase(Locale.ROOT);
        var variant =
                entry.variants.computeIfAbsent(key, ignored -> new VariantEntry(row, region, word));
        merge(
                variant.fields,
                row,
                List.of(
                        "regional_meaning",
                        "regional_meaning_en",
                        "regional_pronunciation",
                        "regional_audio_url",
                        "regional_example_vi",
                        "regional_example_en",
                        "regional_usage_note",
                        "regional_usage_note_en"),
                errors);
    }

    /** Kiem tra gioi han do dai cua truong du lieu import. */
    private void length(Row row, String field, String value, int maximum, List<Error> errors) {
        if (value.length() > maximum)
            errors.add(error(row, field, "Tối đa " + maximum + " ký tự."));
    }

    /** Gop gia tri cac dong cung muc tu va bao loi khi noi dung bi mau thuan. */
    private void merge(
            Map<String, String> target, Row row, List<String> names, List<Error> errors) {
        for (String name : names) {
            String text = value(row, name);
            if (text.isBlank()) continue;
            String previous = target.putIfAbsent(name, text);
            if (previous != null && !previous.equals(text))
                errors.add(
                        error(
                                row,
                                name,
                                "Nội dung không thống nhất trong cùng mục từ/nghĩa. Dùng"
                                        + " meaning_order khác nếu đây là nghĩa khác."));
        }
    }

    /** Doc thu tu nghia hoac vi du va ghi nhan loi neu khong hop le. */
    private int order(Row row, String field, int fallback, List<Error> errors) {
        if (value(row, field).isBlank()) return fallback;
        try {
            int n = Integer.parseInt(value(row, field));
            if (n < 0 || n > 20000) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException failure) {
            errors.add(error(row, field, "Thứ tự phải là số nguyên từ 0 đến 20.000."));
            return fallback;
        }
    }

    /** Tao thong tin loi kem sheet, dong va cot de nguoi dung sua du lieu. */
    private Error error(Row row, String column, String message) {
        return new Error(type().sheet(), row.rowNumber(), column, message);
    }

    /** Doc gia tri o Excel theo kieu du lieu duoc ho tro. */
    private static String value(Row row, String name) {
        return row.values().getOrDefault(name, "").strip();
    }

    /** Chuyen gia tri rong thanh null theo quy uoc du lieu hien tai. */
    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    /** Tim tu chuan da ton tai de tranh nhap trung. */
    private Optional<VocabularyWord> findExisting(Entry entry) {
        String normalized = VocabularyText.normalize(entry.word);
        return words.findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(entry.word, entry.part).stream()
                .filter(
                        candidate ->
                                VocabularyText.normalize(candidate.getWord())
                                        .equalsIgnoreCase(normalized))
                .findFirst();
    }

    /** Chuyen thong tin tu chuan sang muc ket qua import. */
    private Map<String, Object> wordItem(Entry entry) {
        return Map.of("word", entry.word, "partOfSpeech", entry.part);
    }

    /** Chuyen bien the vung mien sang muc ket qua import. */
    private Map<String, Object> variantItem(Entry entry, VariantEntry variant) {
        return Map.of(
                "masterWord",
                entry.word,
                "masterPartOfSpeech",
                entry.part,
                "region",
                variant.region,
                "word",
                variant.word);
    }

    /** Tong hop ket qua import de hien thi va luu lich su. */
    private Map<String, Object> summary(
            String firstKey,
            List<Map<String, Object>> first,
            String secondKey,
            List<Map<String, Object>> second,
            String thirdKey,
            List<Map<String, Object>> third,
            String fourthKey,
            List<Map<String, Object>> fourth) {
        var result = new LinkedHashMap<String, Object>();
        result.put(firstKey, List.copyOf(first));
        result.put(secondKey, List.copyOf(second));
        result.put(thirdKey, List.copyOf(third));
        result.put(fourthKey, List.copyOf(fourth));
        return result;
    }

    private static class Entry {
        final Row first;
        final String word, part;
        final Map<String, String> fields = new HashMap<>();
        final SortedMap<Integer, Meaning> meanings = new TreeMap<>();
        final Map<String, VariantEntry> variants = new LinkedHashMap<>();
        VocabularyStatus status = VocabularyStatus.DRAFT;

        Entry(Row first, String word, String part) {
            this.first = first;
            this.word = word;
            this.part = part;
        }

        VocabularyRequest request() {
            return new VocabularyRequest(
                    word,
                    fields.get("pronunciation"),
                    part,
                    fields.get("audio_url"),
                    status,
                    meanings.values().stream().map(Meaning::request).toList());
        }
    }

    private static class VariantEntry {
        final Row first;
        final String region, word;
        final Map<String, String> fields = new HashMap<>();

        VariantEntry(Row first, String region, String word) {
            this.first = first;
            this.region = region;
            this.word = word;
        }

        RegionalVariantImportPort.Variant value() {
            return new RegionalVariantImportPort.Variant(
                    region,
                    word,
                    fields.getOrDefault("regional_meaning", ""),
                    blankToNull(fields.get("regional_meaning_en")),
                    blankToNull(fields.get("regional_pronunciation")),
                    blankToNull(fields.get("regional_audio_url")),
                    blankToNull(fields.get("regional_example_vi")),
                    blankToNull(fields.get("regional_example_en")),
                    blankToNull(fields.get("regional_usage_note")),
                    blankToNull(fields.get("regional_usage_note_en")));
        }
    }

    private static class Meaning {
        final int order;
        final Map<String, String> fields = new HashMap<>();
        final SortedMap<Integer, VocabularyExampleRequest> examples = new TreeMap<>();

        Meaning(int order) {
            this.order = order;
        }

        VocabularyMeaningRequest request() {
            return new VocabularyMeaningRequest(
                    fields.get("translation_en"),
                    fields.get("definition_en"),
                    fields.get("usage_note"),
                    order,
                    List.copyOf(examples.values()),
                    fields.get("definition_vi"),
                    fields.getOrDefault("labels", "")
                            .lines()
                            .map(String::strip)
                            .filter(s -> !s.isBlank())
                            .distinct()
                            .toList());
        }
    }
}
