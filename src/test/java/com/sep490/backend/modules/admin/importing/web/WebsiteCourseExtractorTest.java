package com.sep490.backend.modules.admin.importing.web;

import static org.assertj.core.api.Assertions.*;

import com.sep490.backend.common.exception.ContentException;
import org.junit.jupiter.api.Test;

class WebsiteCourseExtractorTest {
  private final SafeWebFetcher fetcher = new SafeWebFetcher();
  private final WebsiteCourseExtractor extractor = new WebsiteCourseExtractor(fetcher);

  @Test
  void preservesVietnameseFormattingAndDomMediaOrder() {
    String html = """
        <html><head><title>Basic Vietnamese</title><meta name="author" content="Tung Hoang"></head><body>
        <article><h1 class="entry-title">Section 1: Vietnamese Alphabet</h1>
        <div class="entry-content">
          <h2>The Alphabet</h2>
          <p>The Vietnamese Alphabet consists of <strong>29 letters</strong>: Ă, Â, Đ, Ê, Ô, Ơ, Ư.</p>
          <figure><picture><source srcset="/small.webp 320w, /original.webp 1600w"><img src="/thumb.jpg" alt="Bảng chữ cái" width="1600" height="900"></picture><figcaption>Bảng chữ cái tiếng Việt</figcaption></figure>
          <audio controls><source src="/alphabet.mp3" type="audio/mpeg"></audio>
          <table><tbody><tr><th>Chữ</th><th>IPA</th></tr><tr><td>Đ</td><td>/ɗ/</td></tr></tbody></table>
          <script>alert('xss')</script>
          <iframe src="https://example.org/exercise/1" title="Practice"></iframe>
        </div></article></body></html>
        """;

    var result = extractor.extractHtml(html, "https://openbooks.lib.msu.edu/vietnamese/chapter/alphabet/");
    var lesson = result.modules().getFirst().lessons().getFirst();

    assertThat(lesson.title()).isEqualTo("Section 1: Vietnamese Alphabet");
    assertThat(lesson.blocks()).extracting(WebsiteImportModel.BlockDraft::type)
        .containsExactly("HEADING", "PARAGRAPH", "IMAGE", "AUDIO", "TABLE", "VIDEO");
    assertThat(lesson.blocks()).extracting(WebsiteImportModel.BlockDraft::orderIndex)
        .containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(lesson.blocks().get(1).html()).contains("<strong>29 letters</strong>").doesNotContain("script");
    assertThat(lesson.blocks().get(1).text()).contains("Ă, Â, Đ, Ê, Ô, Ơ, Ư");
    assertThat(lesson.blocks().get(2).mediaUrl()).isEqualTo("https://openbooks.lib.msu.edu/original.webp");
    assertThat(lesson.blocks().get(2).caption()).isEqualTo("Bảng chữ cái tiếng Việt");
    assertThat(lesson.blocks().get(2).altText()).isEqualTo("Bảng chữ cái");
    assertThat(lesson.blocks().get(4).html()).contains("<table>", "/ɗ/");
    assertThat(lesson.blocks().get(5).reviewRequired()).isTrue();
  }

  @Test
  void rejectsPrivateAndNonHttpSourcesBeforeRequest() {
    assertThatThrownBy(() -> fetcher.publicUri("http://127.0.0.1/admin"))
        .isInstanceOf(ContentException.class).hasMessageContaining("nội bộ");
    assertThatThrownBy(() -> fetcher.publicUri("file:///etc/passwd"))
        .isInstanceOf(ContentException.class).hasMessageContaining("http(s)");
    assertThatThrownBy(() -> fetcher.publicUri("http://169.254.169.254/latest/meta-data"))
        .isInstanceOf(ContentException.class).hasMessageContaining("nội bộ");
  }

  @Test
  void importsMultiChapterXhtmlExportWithoutChangingText() {
    String xhtml = """
        <!doctype html><html><head><title>Basic Vietnamese Export</title></head><body>
        <section class="part"><h1>Chapter 1: Greetings</h1>
          <section class="chapter"><h1>Conversation</h1><p><strong>A:</strong> Chào bạn, mình là Hoa.</p><audio src="https://cdn.example.org/conversation.mp3"></audio></section>
          <section class="chapter"><h1>Grammar</h1><p>Structure: A là B</p><table><tr><td>Tôi là Nam.</td><td>I am Nam.</td></tr></table></section>
        </section></body></html>
        """;
    var result = extractor.extractFile(xhtml.getBytes(java.nio.charset.StandardCharsets.UTF_8), "basic-vietnamese.xhtml");
    assertThat(result.modules()).hasSize(1);
    assertThat(result.modules().getFirst().lessons()).extracting(WebsiteImportModel.LessonDraft::title)
        .containsExactly("Conversation", "Grammar");
    assertThat(result.modules().getFirst().lessons().getFirst().blocks())
        .extracting(WebsiteImportModel.BlockDraft::type).contains("AUDIO");
    assertThat(result.modules().getFirst().lessons().get(1).blocks())
        .extracting(WebsiteImportModel.BlockDraft::type).contains("TABLE");
  }

  @Test
  void importsPressbooksWordpressXmlPartsAndChapters() {
    String xml = """
        <?xml version="1.0" encoding="UTF-8"?><rss xmlns:wp="http://wordpress.org/export/1.2/" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel>
        <title>Basic Vietnamese</title><dc:creator>Tung Hoang</dc:creator>
        <item><title>Chapter 1</title><wp:post_id>10</wp:post_id><wp:post_type>part</wp:post_type></item>
        <item><title>Vocabulary</title><link>https://example.org/vocabulary</link><wp:post_parent>10</wp:post_parent><wp:post_type>chapter</wp:post_type><content:encoded><![CDATA[<h2>Từ vựng</h2><p>Xin chào — Hello</p>]]></content:encoded></item>
        </channel></rss>
        """;
    var result = extractor.extractFile(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8), "pressbooks.xml");
    assertThat(result.title()).isEqualTo("Basic Vietnamese");
    assertThat(result.author()).isEqualTo("Tung Hoang");
    assertThat(result.modules()).singleElement().satisfies(module -> {
      assertThat(module.title()).isEqualTo("Chapter 1");
      assertThat(module.lessons()).singleElement().satisfies(lesson -> {
        assertThat(lesson.title()).isEqualTo("Vocabulary");
        assertThat(lesson.blocks()).anySatisfy(block -> assertThat(block.text()).contains("Xin chào"));
      });
    });
  }
}
