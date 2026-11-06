package com.sep490.backend.modules.learning.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionBankServiceTests {
  private ContentStore db;
  private QuestionBankService service;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    service = new QuestionBankService(db, new QuestionScoring());
    when(db.encode(any())).thenAnswer(invocation -> invocation.getArgument(0).toString());
  }

  @Test
  void createsQuestionSnapshotAndRecordsAuthor() throws Exception {
    when(db.insert(startsWith("INSERT INTO questions("), any(Object[].class))).thenReturn(41L);
    when(db.one("SELECT prompt_vi,explanation_vi FROM questions WHERE id=?", 41L))
        .thenReturn(Map.of("promptVi", "Chọn", "explanationVi", "Giải thích"));

    long id = service.create(question(null, null, "PUBLISHED"), 7L);

    assertThat(id).isEqualTo(41L);
    verify(db).update("UPDATE questions SET created_by=? WHERE id=?", 7L, 41L);
    verify(db).insert(startsWith("INSERT INTO question_versions"), eq(41L), eq(1L),
        eq("MULTIPLE_CHOICE"), eq("Choose"), any(), eq("EASY"), isNull(), isNull(),
        anyString(), anyString(), eq("Chọn"), eq("Giải thích"));
  }

  @Test
  void rejectsUnsupportedDifficultyAndUnsafeMedia() throws Exception {
    assertThatThrownBy(() -> service.create(question(null, null, "PUBLISHED", "IMPOSSIBLE")))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Unsupported value");
    assertThatThrownBy(() -> service.create(question("file:///image.png", null, "PUBLISHED")))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Media URL");
  }

  @Test
  void onlyQuestionOwnerCanEdit() {
    when(db.count("SELECT count(*) FROM questions WHERE id=? AND created_by=?", 41L, 7L))
        .thenReturn(0L);

    assertThatThrownBy(() -> service.assertCanEdit(41L, 7L))
        .isInstanceOfSatisfying(ContentException.class,
            error -> assertThat(error.code).isEqualTo("QUESTION_NOT_OWNED"));
  }

  @Test
  void editingCreatesNextImmutableVersion() throws Exception {
    when(db.one("SELECT * FROM questions WHERE id=? FOR UPDATE", 41L))
        .thenReturn(Map.of("id", 41L, "currentVersion", 2L));
    when(db.one("SELECT prompt_vi,explanation_vi FROM questions WHERE id=?", 41L))
        .thenReturn(Map.of("promptVi", "Chọn", "explanationVi", "Giải thích"));

    service.edit(41L, question(null, null, "DRAFT"));

    verify(db).update(startsWith("UPDATE questions SET"), eq("MULTIPLE_CHOICE"), eq("Choose"),
        any(), eq("EASY"), any(), isNull(), isNull(), eq("DRAFT"), eq(3L), eq(41L));
    verify(db).insert(startsWith("INSERT INTO question_versions"), eq(41L), eq(3L),
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  private ContentRequests.Question question(String image, String audio, String status) throws Exception {
    return question(image, audio, status, "EASY");
  }

  private ContentRequests.Question question(String image, String audio, String status, String difficulty)
      throws Exception {
    return new ContentRequests.Question(
        "MULTIPLE_CHOICE", "Choose", "Explanation", difficulty, "Greeting", image, audio, status,
        json.readTree("{\"optionIds\":[\"a\"]}"),
        json.readTree("[{\"id\":\"a\",\"text\":\"Hello\"},{\"id\":\"b\",\"text\":\"Bye\"}]"),
        "Chọn", "Giải thích");
  }
}
