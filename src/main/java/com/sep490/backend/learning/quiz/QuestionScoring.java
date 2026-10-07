package com.sep490.backend.learning.quiz;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.learning.shared.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Component;

/** Exact, deterministic grading; accents are significant, whitespace is normalized. */
@Component
public class QuestionScoring {
  public static final Set<String> TYPES =
      Set.of(
          "MULTIPLE_CHOICE",
          "MULTIPLE_SELECT",
          "TRUE_FALSE",
          "FILL_BLANK",
          "MATCHING",
          "LISTENING",
          "TRANSLATION",
          "REORDER_SENTENCE",
          "REORDER",
          "IMAGE_SELECTION");
  private static final Set<String> CHOICE =
      Set.of("MULTIPLE_CHOICE", "MULTIPLE_SELECT", "TRUE_FALSE", "IMAGE_SELECTION");

  public void validate(String type, JsonNode options, JsonNode correct) {
    ContentRules.member(type, TYPES);
    if (!correct.isObject() || !options.isArray() || options.size() > 100)
      throw ContentException.invalid(
          "Provide an answer configuration object and an options array.");
    Set<String> ids = new HashSet<>();
    for (var o : options) {
      if (!o.isObject() || o.path("id").asText().isBlank() || !ids.add(o.path("id").asText()))
        throw ContentException.invalid("Options need unique nonempty IDs.");
      ContentRules.url(o.path("imageUrl").asText(null));
      ContentRules.url(o.path("audioUrl").asText(null));
    }
    if (CHOICE.contains(type) || (type.equals("LISTENING") && !options.isEmpty())) {
      JsonNode a = correct.path("optionIds");
      if (!a.isArray() || a.isEmpty() || (!type.equals("MULTIPLE_SELECT") && a.size() != 1))
        throw ContentException.invalid("Select the correct option(s).");
      Set<String> selected = new HashSet<>();
      for (var x : a)
        if (!ids.contains(x.asText()) || !selected.add(x.asText()))
          throw ContentException.invalid(
              "Correct option IDs must be unique and present in options.");
      if (ids.size() < 2) throw ContentException.invalid("At least two options are required.");
    } else if (type.equals("MATCHING")) {
      var pairs = correct.path("pairs");
      if (!pairs.isObject() || pairs.isEmpty())
        throw ContentException.invalid("Matching requires a nonempty pairs object.");
      pairs
          .fields()
          .forEachRemaining(
              e -> {
                if (!ids.contains(e.getKey()) || !ids.contains(e.getValue().asText()))
                  throw ContentException.invalid("Matching pairs must reference option IDs.");
              });
    } else if ((type.equals("REORDER_SENTENCE") || type.equals("REORDER"))) {
      var sequence = correct.path("sequence");
      if (!sequence.isArray() || sequence.isEmpty())
        throw ContentException.invalid("Reorder requires a sequence of option IDs.");
      List<String> order = new ArrayList<>();
      sequence.forEach(x -> order.add(x.asText()));
      if (new HashSet<>(order).size() != order.size() || !new HashSet<>(order).equals(ids))
        throw ContentException.invalid("Sequence must include each token ID once.");
    } else {
      var answers = correct.path("acceptedAnswers");
      if (!answers.isArray() || answers.isEmpty())
        throw ContentException.invalid("Provide accepted text answers.");
      for (var a : answers)
        if (!a.isTextual() || a.asText().isBlank())
          throw ContentException.invalid("Accepted answers must be nonempty strings.");
    }
  }

  public boolean correct(String type, JsonNode options, JsonNode key, JsonNode answer) {
    if (answer == null || answer.isNull()) return false;
    if (CHOICE.contains(type) || (type.equals("LISTENING") && !options.isEmpty())) {
      JsonNode selected = answer.path("optionIds");
      if (!selected.isArray()) return false;
      Set<String> a = new HashSet<>(), b = new HashSet<>();
      selected.forEach(v -> a.add(v.asText()));
      key.path("optionIds").forEach(v -> b.add(v.asText()));
      return selected.size() == a.size() && a.equals(b);
    }
    if (type.equals("MATCHING")) return key.path("pairs").equals(answer.path("pairs"));
    if ((type.equals("REORDER_SENTENCE") || type.equals("REORDER")))
      return key.path("sequence").equals(answer.path("sequence"));
    String text = answer.isTextual() ? answer.asText() : answer.path("text").asText("");
    boolean sensitive = key.path("caseSensitive").asBoolean(false);
    for (var expected : key.path("acceptedAnswers"))
      if (normalize(expected.asText(), sensitive).equals(normalize(text, sensitive))) return true;
    return false;
  }

  private String normalize(String s, boolean sensitive) {
    String n = Normalizer.normalize(s, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
    return sensitive ? n : n.toLowerCase(Locale.ROOT);
  }

  public List<Map<String, Object>> safeOptions(JsonNode options) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (var o : options) {
      Map<String, Object> safe = new LinkedHashMap<>();
      for (String key : List.of("id", "text", "textVi", "textEn", "imageUrl", "audioUrl", "side"))
        if (o.hasNonNull(key)) safe.put(key, o.get(key).asText());
      result.add(safe);
    }
    return result;
  }
}
