package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.learning.quiz.QuestionScoring;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionScoringTests {
  private final ObjectMapper json = new ObjectMapper();
  private final QuestionScoring scoring = new QuestionScoring();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "MULTIPLE_CHOICE",
        "TRUE_FALSE",
        "IMAGE_SELECTION",
        "LISTENING",
        "MULTIPLE_SELECT"
      })
  void choiceRequiresExactUniqueSelection(String type) throws Exception {
    var options = json.readTree("[{\"id\":\"a\"},{\"id\":\"b\"}]");
    var key = json.readTree("{\"optionIds\":[\"a\"]}");
    scoring.validate(type, options, key);
    assertThat(scoring.correct(type, options, key, key)).isTrue();
    assertThat(scoring.correct(type, options, key, json.readTree("{\"optionIds\":[\"a\",\"b\"]}")))
        .isFalse();
    assertThat(scoring.correct(type, options, key, json.readTree("{\"optionIds\":[\"a\",\"a\"]}")))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"FILL_BLANK", "TRANSLATION", "LISTENING"})
  void textNormalizesUnicodeAndWhitespaceButPreservesAccents(String type) throws Exception {
    var options = json.readTree("[]");
    var key = json.readTree("{\"acceptedAnswers\":[\"Cảm ơn\"]}");
    scoring.validate(type, options, key);
    assertThat(scoring.correct(type, options, key, json.readTree("{\"text\":\"  CẢM   ƠN  \"}")))
        .isTrue();
    assertThat(scoring.correct(type, options, key, json.readTree("{\"text\":\"Cam on\"}")))
        .isFalse();
  }

  @Test
  void matchingAndReorderingRequireCompleteAnswers() throws Exception {
    var options = json.readTree("[{\"id\":\"a\"},{\"id\":\"b\"}]");
    var pairs = json.readTree("{\"pairs\":{\"a\":\"b\"}}");
    scoring.validate("MATCHING", options, pairs);
    assertThat(scoring.correct("MATCHING", options, pairs, pairs)).isTrue();
    assertThat(scoring.correct("MATCHING", options, pairs, json.readTree("{}"))).isFalse();
    var sequence = json.readTree("{\"sequence\":[\"a\",\"b\"]}");
    scoring.validate("REORDER_SENTENCE", options, sequence);
    scoring.validate("REORDER", options, sequence);
    assertThat(scoring.correct("REORDER", options, sequence, sequence)).isTrue();
    assertThat(scoring.correct("REORDER", options, sequence, json.readTree("{\"sequence\":[\"b\",\"a\"]}"))).isFalse();
    assertThat(scoring.correct("REORDER_SENTENCE", options, sequence, sequence)).isTrue();
    assertThat(
            scoring.correct(
                "REORDER_SENTENCE",
                options,
                sequence,
                json.readTree("{\"sequence\":[\"b\",\"a\"]}")))
        .isFalse();
  }
}
