package com.sep490.backend.service.importing;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.dto.model.DictionaryMetadata.*;
import com.sep490.backend.dto.request.*;
import com.sep490.backend.dto.response.ContentImportResponse.*;
import com.sep490.backend.dto.response.ContentImportResponse.Error;
import com.sep490.backend.entity.enums.VocabularyStatus;
import com.sep490.backend.service.VocabularyText;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class DictionaryJsonlReader {
    private final ObjectMapper json;
    private final DictionarySources sources;
    public record Result(List<Row> rows,List<Error> errors,Map<String,Object> summary) {}
    public record Entry(VocabularyWordRequest request,Source source,Lexical lexical,String raw) {}
    private static final Set<String> POS = Set.of("noun","verb","adj","adv","pron","num","particle","conj","prep","intj","phrase","proverb","name","abbrev","det","prefix","suffix","affix","classifier","counter","character","romanization");
    private static final String MODIFICATIONS = "Chuẩn hóa Unicode NFC và khoảng trắng; gộp cùng từ/từ loại, giữ các nghĩa và ngữ cảnh nguồn. Không tự sinh định nghĩa, dịch hoặc CEFR. Media và ví dụ trích dẫn bên ngoài không được nhập. Cần kiểm duyệt nội dung trước khi xuất bản.";

    public Result read(InputStream input,String filename) {
        var errors = new ArrayList<Error>(); var rows = new ArrayList<Row>(); var stats = new LinkedHashMap<String,Object>();
        int read=0, vietnamese=0, other=0, missingPos=0, missingDefinitions=0, invalid=0, duplicates=0, omittedExamples=0, omittedMedia=0, aligned=0;
        var groups = new LinkedHashMap<String,List<JsonNode>>(); var hashes = new HashSet<String>();
        var firstLines = new HashMap<String,Integer>();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".jsonl"))
            return new Result(List.of(),List.of(error(0,"file","Chọn file JSONL pilot có checksum đã đăng ký.")),Map.of());
        String fileHash = "";
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var counted = new LimitedInputStream(input,ExcelCourseParser.MAX_BYTES);
            try (var stream = new BufferedReader(new InputStreamReader(new DigestInputStream(counted,digest),StandardCharsets.UTF_8))) {
                String line; int physicalLine=0;
                while ((line = stream.readLine()) != null) {
                    physicalLine++;
                    if (line.isBlank()) continue;
                    if (++read > 20000) throw new IOException("Row limit");
                    JsonNode node;
                    try { node=json.readTree(line); if (!node.isObject()) throw new IOException("Object required"); }
                    catch (Exception failure) { invalid++; errors.add(error(physicalLine,"json","Mỗi dòng phải là một JSON object hợp lệ.")); continue; }
                    if (!"vi".equals(node.path("lang_code").asText())) { other++; continue; }
                    vietnamese++;
                    var word=VocabularyText.normalize(node.path("word").isTextual() ? node.path("word").asText() : ""); var part=node.path("pos").asText();
                    if (!POS.contains(part)) { missingPos++; errors.add(error(physicalLine,"pos","Thiếu hoặc chưa hỗ trợ từ loại từ nguồn.")); continue; }
                    if (word.isBlank() || word.length()>200 || word.contains(":") || word.codePoints().anyMatch(Character::isISOControl)) {
                        invalid++; errors.add(error(physicalLine,"word","Mục từ không hợp lệ hoặc là trang kỹ thuật.")); continue;
                    }
                    if (!node.path("senses").isArray() || node.path("senses").isEmpty() || anyMissingGloss(node.path("senses"))) {
                        missingDefinitions++; errors.add(error(physicalLine,"senses","Thiếu định nghĩa; không tự bỏ nghĩa để nhập phần còn lại.")); continue;
                    }
                    String hash=sha(json.writeValueAsString(node));
                    if (!hashes.add(hash)) { duplicates++; continue; }
                    String key=word.toLowerCase(Locale.ROOT)+"\u0000"+part.toLowerCase(Locale.ROOT);
                    groups.computeIfAbsent(key,k -> new ArrayList<>()).add(node); firstLines.putIfAbsent(key,physicalLine);
                }
            }
            fileHash=HexFormat.of().formatHex(digest.digest());
            if (!sources.accepts(fileHash)) errors.add(error(0,"source","Checksum chưa được đăng ký: chưa thể xác minh nguồn/giấy phép của file. Dùng pilot được cung cấp hoặc xác minh manifest mới trước khi nhập."));
            for (var group:groups.entrySet()) {
                var nodes=group.getValue(); var first=nodes.getFirst(); String word=VocabularyText.normalize(first.path("word").asText());
                var meanings=new ArrayList<VocabularyMeaningRequest>(); var pronunciations=new ArrayList<Pronunciation>();
                var related=new ArrayList<RelatedWord>(); var notes=new ArrayList<String>(); var etymologies=new ArrayList<String>();
                for (var node:nodes) {
                    for (var sound:node.path("sounds")) {
                        if (sound.hasNonNull("audio") || sound.hasNonNull("mp3_url") || sound.hasNonNull("ogg_url")) omittedMedia++;
                        if (!sound.path("ipa").asText().isBlank()) pronunciations.add(new Pronunciation(sound.path("ipa").asText(),labels(sound)));
                    }
                    notes.addAll(strings(node.path("notes"))); etymologies.addAll(strings(node.path("etymology_texts")));
                    for (String kind:List.of("synonyms","antonyms","related","derived")) for(var relation:node.path(kind)) {
                        String target=VocabularyText.normalize(relation.path("word").asText());
                        if (!target.isBlank() && target.length()<=200) related.add(new RelatedWord(target,kind,relation.path("sense").asText(null)));
                    }
                    for (var sense:node.path("senses")) {
                        String definition=String.join("\n",strings(sense.path("glosses")));
                        var translations=new LinkedHashSet<String>();
                        for (var translation:node.path("translations")) if ("en".equals(translation.path("lang_code").asText())) {
                            String target=VocabularyText.normalize(translation.path("word").asText());
                            String scope=VocabularyText.normalize(translation.path("sense").asText());
                            boolean match=scope.isEmpty() ? node.path("senses").size()==1 : strings(sense.path("glosses")).stream().anyMatch(g -> VocabularyText.normalize(g).equalsIgnoreCase(scope));
                            if (match && !target.isBlank()) translations.add(target);
                        }
                        for(var translation:sense.path("translations")) if ("en".equals(translation.path("lang_code").asText()) && !translation.path("word").asText().isBlank()) translations.add(translation.path("word").asText());
                        String english=String.join("; ",translations); if (english.length()>500) english="";
                        if (!english.isBlank()) aligned++;
                        var examples=new ArrayList<VocabularyExampleRequest>();
                        for(var example:sense.path("examples")) {
                            if (example.hasNonNull("ref") || example.hasNonNull("reference") || example.hasNonNull("source")) { omittedExamples++; continue; }
                            if (!example.path("text").asText().isBlank()) examples.add(new VocabularyExampleRequest(VocabularyText.normalize(example.path("text").asText()),null,null,examples.size()));
                        }
                        meanings.add(new VocabularyMeaningRequest(english.isBlank()?null:english,null,null,meanings.size(),examples,definition,labels(sense)));
                        for(String kind:List.of("synonyms","antonyms","related")) for(var relation:sense.path(kind))
                            if (!relation.path("word").asText().isBlank()) related.add(new RelatedWord(relation.path("word").asText(),kind,definition));
                    }
                }
                var p=sources.profile(); String encoded=URLEncoder.encode(word.replace(' ','_'),StandardCharsets.UTF_8).replace("+","%20");
                String raw=json.writeValueAsString(nodes); String sourceUrl="https://vi.wiktionary.org/wiki/"+encoded;
                var source=new Source(p.path("id").asText(),p.path("name").asText(),"viwiktionary",sourceUrl,
                        "https://vi.wiktionary.org/w/index.php?title="+encoded+"&action=history",p.path("downloadUrl").asText(),p.path("dumpDate").asText(),
                        p.path("extractedAt").asText(),p.path("retrievedAt").asText(),p.path("license").asText(),p.path("licenseUrl").asText(),
                        p.path("attribution").asText(),p.path("modifications").asText()+" "+MODIFICATIONS,fileHash,sha(raw),false);
                var request=new VocabularyWordRequest(word,pronunciations.isEmpty()?null:pronunciations.getFirst().ipa(),first.path("pos").asText(),null,null,VocabularyStatus.PENDING_REVIEW,Set.of(),meanings);
                var lexical=new Lexical(pronunciations.stream().distinct().toList(),related.stream().distinct().toList(),etymologies.stream().distinct().toList(),notes.stream().distinct().toList());
                var fields=new LinkedHashMap<String,String>(); fields.put("word",word); fields.put("part_of_speech",request.partOfSpeech());
                fields.put("senses",String.valueOf(meanings.size())); fields.put("definition_vi",meanings.getFirst().definitionVi());
                fields.put("translation_en",Objects.toString(meanings.getFirst().translationEn(),"")); fields.put("status","PENDING_REVIEW"); fields.put("action","CREATE");
                fields.put("_dictionary",json.writeValueAsString(new Entry(request,source,lexical,raw))); rows.add(new Row(firstLines.get(group.getKey()),fields));
            }
        } catch (IOException | GeneralSecurityException failure) { errors.add(error(0,"file","Không đọc được JSONL hoặc file vượt giới hạn upload 10 MiB/20.000 dòng.")); }
        if (read==0) errors.add(error(0,"file","File không có dữ liệu."));
        stats.put("recordsRead",read); stats.put("vietnameseRecords",vietnamese); stats.put("excludedLanguages",other);
        stats.put("invalidRecords",invalid); stats.put("missingDefinitions",missingDefinitions); stats.put("missingPos",missingPos);
        stats.put("duplicateRecords",duplicates); stats.put("normalizedEntries",rows.size()); stats.put("englishAlignedSenses",aligned);
        stats.put("quotedExamplesOmitted",omittedExamples); stats.put("mediaOmitted",omittedMedia); stats.put("sha256",fileHash);
        stats.put("source",sources.profile()); stats.put("warnings",List.of("Nội dung chỉ đạt kiểm tra cấu trúc; cần Admin kiểm duyệt từng nghĩa trước khi xuất bản.","Không tự gán CEFR; không suy đoán bản dịch hoặc giấy phép media."));
        return new Result(rows,errors,stats);
    }
    private static boolean anyMissingGloss(JsonNode senses) { for(var sense:senses) if(strings(sense.path("glosses")).isEmpty()) return true; return false; }
    private static List<String> strings(JsonNode node) {
        var result=new ArrayList<String>();
        if(node.isArray()) for(var text:node) if(text.isTextual()) {
            String value=VocabularyText.normalize(text.asText().replace("__NOEDITSECTION__",""));
            if(!value.isBlank()) result.add(value);
        }
        return result;
    }
    private static List<String> labels(JsonNode node) { var result=new ArrayList<>(strings(node.path("tags"))); result.addAll(strings(node.path("raw_tags"))); return result.stream().distinct().toList(); }
    private static Error error(int row,String column,String message) { return new Error("JSONL",row,column,message); }
    private static String sha(String text) throws NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    private static class LimitedInputStream extends FilterInputStream {
        private long count; private final long limit;
        LimitedInputStream(InputStream input,long limit) { super(input); this.limit=limit; }
        public int read() throws IOException { int n=super.read(); if(n>=0&&++count>limit) throw new IOException("Size limit"); return n; }
        public int read(byte[] bytes,int off,int len) throws IOException { int n=in.read(bytes,off,len); if(n>0&&(count+=n)>limit) throw new IOException("Size limit"); return n; }
    }
}
