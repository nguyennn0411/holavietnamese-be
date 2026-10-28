package com.sep490.backend.modules.vocabulary.dto.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** Source licensing applies to imported lexical content, not to application code. */
public final class DictionaryMetadata {
    private static final ObjectMapper JSON = new ObjectMapper();
    private DictionaryMetadata() {}
    public record Source(String id, String name, String edition, String sourceUrl, String historyUrl,
            String downloadUrl, String dumpDate, String extractedAt, String retrievedAt,
            String license, String licenseUrl, String attribution, String modifications,
            String inputSha256, String entrySha256, boolean edited) {
        public Source markEdited() { return new Source(id,name,edition,sourceUrl,historyUrl,downloadUrl,dumpDate,
                extractedAt,retrievedAt,license,licenseUrl,attribution,modifications,inputSha256,entrySha256,true); }
    }
    public record Pronunciation(String ipa, List<String> labels) {}
    public record RelatedWord(String word, String kind, String sense) {}
    public record Lexical(List<Pronunciation> pronunciations, List<RelatedWord> relatedWords,
            List<String> etymologies, List<String> notes) {}
    public static String encode(Object value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot store dictionary metadata", e); }
    }
    public static <T> T decode(String value, Class<T> type) {
        if (value == null || value.isBlank()) return null;
        try { return JSON.readValue(value,type); } catch (Exception e) { throw new IllegalStateException("Cannot read dictionary metadata", e); }
    }
    public static List<String> labels(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return JSON.readValue(value,new TypeReference<List<String>>() {}); } catch (Exception e) { throw new IllegalStateException("Cannot read labels", e); }
    }
}
