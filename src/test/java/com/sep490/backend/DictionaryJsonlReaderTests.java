package com.sep490.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.service.VocabularyText;
import com.sep490.backend.service.importing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DictionaryJsonlReaderTests {
    final ObjectMapper json=new ObjectMapper();
    final DictionarySources sources=new DictionarySources(json);
    final DictionaryJsonlReader reader=new DictionaryJsonlReader(json,sources);
    DictionaryJsonlReaderTests() throws Exception {}
    DictionaryJsonlReader.Result read(String input) { return reader.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),"test.jsonl"); }
    DictionaryJsonlReader.Entry entry(DictionaryJsonlReader.Result result,int row) throws Exception {
        return json.readValue(result.rows().get(row).values().get("_dictionary"),DictionaryJsonlReader.Entry.class);
    }

    @Test void registeredRealPilotRetainsSensesPartsOfSpeechAttributionAndNoMedia() throws Exception {
        try(var input=sources.pilot().getInputStream()) {
            var parsed=reader.read(input,"pilot.jsonl");
            assertThat(parsed.errors()).isEmpty();
            assertThat(parsed.rows()).hasSize(200);
            assertThat(parsed.summary()).containsEntry("recordsRead",205).containsEntry("normalizedEntries",200);
            var entries=new ArrayList<DictionaryJsonlReader.Entry>();
            for(int i=0;i<parsed.rows().size();i++) entries.add(entry(parsed,i));
            assertThat(entries).anyMatch(e -> e.request().meanings().size()>1);
            assertThat(entries.stream().filter(e -> e.request().word().equals("bàn")).map(e -> e.request().partOfSpeech())).contains("noun","verb");
            for(var entry:entries) {
                assertThat(entry.request().status().name()).isEqualTo("PENDING_REVIEW");
                assertThat(entry.request().cefrLevel()).isNull();
                assertThat(entry.request().audioUrl()).isNull();
                assertThat(entry.source().license()).isEqualTo("CC-BY-SA-4.0");
                assertThat(entry.source().historyUrl()).contains("action=history");
                assertThat(entry.source().inputSha256()).isEqualTo(sources.profile().path("acceptedFiles").get(0).path("sha256").asText());
                assertThat(entry.request().meanings()).allMatch(m -> m.definitionVi()!=null&&!m.definitionVi().isBlank());
                assertThat(entry.raw()).contains("_original_record_sha256");
            }
        }
    }

    @Test void translationAlignmentDoesNotSpreadUnscopedEnglishAcrossDifferentSenses() throws Exception {
        var parsed=read("""
          {"word":"bàn","lang_code":"vi","pos":"noun","senses":[{"glosses":["Đồ vật để đặt đồ."]},{"glosses":["Một đơn vị tính."]}],"translations":[{"lang_code":"en","word":"table","sense":"Đồ vật để đặt đồ."},{"lang_code":"en","word":"unscoped"}]}
          {"word":"ăn","lang_code":"vi","pos":"verb","senses":[{"glosses":["Đưa thức ăn vào cơ thể."]}],"translations":[{"lang_code":"en","word":"eat"}]}
          """);
        assertThat(entry(parsed,0).request().meanings().get(0).translationEn()).isEqualTo("table");
        assertThat(entry(parsed,0).request().meanings().get(1).translationEn()).isNull();
        assertThat(entry(parsed,1).request().meanings().getFirst().translationEn()).isEqualTo("eat");
        assertThat(parsed.errors()).anyMatch(e -> e.column().equals("source"));
    }

    @Test void normalizesUnicodeGroupsSameLemmaAndPosAndOmitsCitedExamplesAndAudio() throws Exception {
        String noun="""
          {"word":"  bàn  ","lang_code":"vi","pos":"noun","sounds":[{"ipa":"/ɓaːn/","tags":["Hà Nội"],"audio":"unverified.ogg"}],"senses":[{"glosses":["  Đồ  vật.  "],"examples":[{"text":"Cái bàn.","ref":"External book"},{"text":" Một  cái bàn. "}]}],"notes":["__NOEDITSECTION__"]}
          """;
        String decomposed=Normalizer.normalize("{\"word\":\"bàn\",\"lang_code\":\"vi\",\"pos\":\"noun\",\"senses\":[{\"glosses\":[\"Một nghĩa khác.\"]}]}",Normalizer.Form.NFD);
        var parsed=read(noun+noun+decomposed+"\n{\"word\":\"bàn\",\"lang_code\":\"vi\",\"pos\":\"verb\",\"senses\":[{\"glosses\":[\"Trao đổi ý kiến.\"]}]}\n");
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.summary()).containsEntry("duplicateRecords",1).containsEntry("quotedExamplesOmitted",1).containsEntry("mediaOmitted",1);
        var entry=entry(parsed,0);
        assertThat(entry.request().word()).isEqualTo("bàn");
        assertThat(entry.request().meanings()).hasSize(2);
        assertThat(entry.request().meanings().getFirst().definitionVi()).isEqualTo("Đồ vật.");
        assertThat(entry.request().meanings().getFirst().examples()).hasSize(1);
        assertThat(entry.request().meanings().getFirst().examples().getFirst().exampleVi()).isEqualTo("Một cái bàn.");
        assertThat(entry.lexical().notes()).isEmpty();
        assertThat(entry.lexical().pronunciations()).hasSize(1);
        assertThat(VocabularyText.searchKey(" ĐƯỜNG ")).isEqualTo("duong");
        assertThat(VocabularyText.normalize("\u00a0 bàn \u2003")).isEqualTo("bàn");
    }

    @Test void filtersLanguagesRejectsTechnicalIncompleteAndMalformedRecordsWithPhysicalLineNumbers() {
        var parsed=read("""

          not-json
          {"word":"table","lang_code":"en","pos":"noun","senses":[{"glosses":["table"]}]}
          {"word":"Template:test","lang_code":"vi","pos":"noun","senses":[{"glosses":["metadata"]}]}
          {"word":"từ","lang_code":"vi","pos":"unknown","senses":[{"glosses":["Từ."]}]}
          {"word":"từ","lang_code":"vi","pos":"noun","senses":[{"glosses":["Từ."]},{}]}
          """);
        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.errors()).anyMatch(e -> e.row()==2&&e.column().equals("json"));
        assertThat(parsed.summary()).containsEntry("excludedLanguages",1).containsEntry("invalidRecords",2)
                .containsEntry("missingDefinitions",1).containsEntry("missingPos",1);
        assertThat(read("").errors()).isNotEmpty();
        var nullWord=read("{\"word\":null,\"lang_code\":\"vi\",\"pos\":\"noun\",\"senses\":[{\"glosses\":[\"Từ.\"]}]}\n");
        assertThat(nullWord.rows()).isEmpty();
        assertThat(nullWord.errors()).anyMatch(e -> e.column().equals("word"));
        assertThat(parsed.summary()).containsEntry("vietnameseRecords",3);
        assertThat(reader.read(InputStream.nullInputStream(),"bad.xlsx").errors()).hasSize(1);
    }
}
