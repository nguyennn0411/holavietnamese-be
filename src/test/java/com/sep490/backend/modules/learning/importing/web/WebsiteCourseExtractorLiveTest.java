package com.sep490.backend.modules.learning.importing.web;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Run explicitly with -Dwebsite.import.live=true; kept out of normal offline CI. */
@EnabledIfSystemProperty(named = "website.import.live", matches = "true")
class WebsiteCourseExtractorLiveTest {
  private final WebsiteCourseExtractor extractor = new WebsiteCourseExtractor(new SafeWebFetcher());
  private static final String ROOT = "https://openbooks.lib.msu.edu/vietnamese/chapter/";

  @Test
  void importsTheRequiredVietnameseAlphabetPageWithMediaAndTables() {
    var result = extractor.extractUrl(ROOT + "section-1-vietnamese-alphabet/", "SINGLE_LESSON");
    var lesson = result.modules().getFirst().lessons().getFirst();
    String text = text(lesson);
    assertThat(lesson.title()).containsIgnoringCase("Vietnamese Alphabet");
    assertThat(text).contains("29 letters", "Chữ Quốc ngữ", "Ă", "Đ", "Ư");
    assertThat(lesson.blocks()).extracting(WebsiteImportModel.BlockDraft::type)
        .contains("TABLE", "AUDIO");
    assertThat(lesson.blocks()).extracting(WebsiteImportModel.BlockDraft::orderIndex)
        .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, lesson.blocks().size()).boxed().toList());
  }

  @Test
  void importsChapterOneConversationVocabularyGrammarAndFurtherPractice() {
    var conversation = lesson("u1-conversation/");
    var vocabulary = lesson("u1-vocabulary/");
    var grammar = lesson("u1-grammar/");
    var practice = lesson("u1-further-practice/");
    assertThat(text(conversation)).contains("Rất vui được gặp bạn", "Thầy chào các bạn");
    assertThat(conversation.blocks()).extracting(WebsiteImportModel.BlockDraft::type).contains("AUDIO");
    assertThat(text(vocabulary)).contains("Xin chào", "Sinh viên", "Tiếng Việt");
    assertThat(text(grammar)).contains("A là B", "Tên bạn là gì");
    assertThat(text(practice)).contains("Tên tôi là Hoàng Thanh Tùng", "LUYỆN TẬP");
  }

  private WebsiteImportModel.LessonDraft lesson(String path) {
    return extractor.extractUrl(ROOT + path, "SINGLE_LESSON").modules().getFirst().lessons().getFirst();
  }

  private static String text(WebsiteImportModel.LessonDraft lesson) {
    return lesson.blocks().stream().map(block -> String.valueOf(block.text()) + " " + String.valueOf(block.caption()))
        .collect(Collectors.joining("\n"));
  }
}
