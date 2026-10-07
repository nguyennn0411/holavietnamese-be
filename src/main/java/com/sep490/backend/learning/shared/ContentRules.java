package com.sep490.backend.learning.shared;

import java.util.*;

public final class ContentRules {
  private ContentRules() {}

  public static final Set<String> LEVELS = Set.of("A0", "A1", "A2", "B1", "B2", "C1");
  public static final Set<String> STATUSES = Set.of("DRAFT", "REVIEW", "PUBLISHED", "ARCHIVED");
  public static final Set<String> LESSON_TYPES =
      Set.of("NORMAL", "VOCABULARY", "GRAMMAR", "CONVERSATION", "CULTURE", "REVIEW");
  public static final Set<String> ACTIVITIES =
      Set.of(
          "TEXT",
          "IMAGE",
          "AUDIO",
          "VIDEO",
          "VOCABULARY",
          "FLASHCARD",
          "DIALOGUE",
          "GRAMMAR",
          "SOUND_PATTERN",
          "MULTIPLE_CHOICE",
          "FILL_BLANK",
          "MATCHING",
          "REORDER_SENTENCE",
          "LISTEN_AND_CHOOSE",
          "LISTEN_AND_TYPE",
          "LISTENING",
          "TRANSLATION",
          "PRACTICE",
          "QUIZ",
          "PRONUNCIATION",
          "AI_CONVERSATION",
          "AI_ROLEPLAY");

  public static void member(String value, Set<String> allowed) {
    if (!allowed.contains(value)) throw ContentException.invalid("Unsupported value: " + value);
  }

  public static void url(String value) {
    if (value != null
        && !value.isBlank()
        && !value.matches("https?://[^\\s]+")
        && !value.startsWith("/media/"))
      throw ContentException.invalid("Media URL must use http(s) or /media/.");
  }

  public static void exactOrder(List<Long> desired, List<Long> current) {
    if (desired.size() != current.size()
        || new HashSet<>(desired).size() != desired.size()
        || !new HashSet<>(desired).equals(new HashSet<>(current)))
      throw ContentException.invalid("Reorder must contain each current item exactly once.");
  }
}
