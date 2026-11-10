package com.sep490.backend.modules.learning.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrammarServiceTests {
  private ContentStore db;
  private GrammarService service;

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    service = new GrammarService(db);
  }

  @Test
  void createsDraftGrammarAndOrderedExamples() {
    when(db.insert(anyString(), any(Object[].class))).thenReturn(41L, 101L, 102L);

    long id = service.create(grammar("A1"));

    assertThat(id).isEqualTo(41L);
    verify(db).insert(anyString(), eq("GRAM-1"), eq("basic-greeting"), eq("Greeting"),
        eq("Xin chào + người nghe"), eq("Explanation"), eq("Mistake"), eq("A1"));
    verify(db).insert(anyString(), eq(41L), eq("Xin chào bạn"), eq("Hello"),
        eq("Friendly"), eq(1));
    verify(db).insert(anyString(), eq(41L), eq("Chào anh"), eq("Hello sir"),
        eq("Polite"), eq(2));
  }

  @Test
  void rejectsUnsupportedGrammarLevelBeforeWriting() {
    assertThatThrownBy(() -> service.create(grammar("STARTER")))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Unsupported value");
    verify(db, never()).insert(anyString(), any(Object[].class));
  }

  @Test
  void publicDetailLoadsOnlyPublishedRelationsAndExamples() {
    Map<String, Object> topic = new java.util.LinkedHashMap<>(Map.of("id", 41L, "status", "PUBLISHED"));
    when(db.one("SELECT * FROM grammar_topics WHERE id=? AND status='PUBLISHED'", 41L))
        .thenReturn(topic);
    when(db.rows("SELECT * FROM grammar_examples WHERE grammar_topic_id=? ORDER BY order_index", 41L))
        .thenReturn(new ArrayList<>(List.of(Map.of("id", 1L))));
    when(db.rows(anyString(), eq(41L))).thenReturn(new ArrayList<>(List.of(Map.of("id", 9L))));

    var result = service.detail(41L, false);

    assertThat((List<?>) result.get("examples")).hasSize(1);
    assertThat((List<?>) result.get("relatedLessons")).hasSize(1);
  }

  @Test
  void statusAcceptsOnlySupportedLifecycleValues() {
    assertThatThrownBy(() -> service.status(41L, "REVIEW"))
        .isInstanceOf(ContentException.class);
    when(db.one("SELECT id FROM grammar_topics WHERE id=? FOR UPDATE", 41L))
        .thenReturn(Map.of("id", 41L));
    service.status(41L, "PUBLISHED");
    verify(db).update(
        "UPDATE grammar_topics SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        "PUBLISHED", 41L);
  }

  private static ContentRequests.Grammar grammar(String level) {
    return new ContentRequests.Grammar(
        "GRAM-1", "basic-greeting", "Greeting", "Xin chào + người nghe", "Explanation",
        "Mistake", level,
        List.of(
            new ContentRequests.Example("Xin chào bạn", "Hello", "Friendly"),
            new ContentRequests.Example("Chào anh", "Hello sir", "Polite")),
        "Description", "Notes", "Lời chào", "Mô tả", "Giải thích", "Lỗi", "Greeting formula");
  }
}
