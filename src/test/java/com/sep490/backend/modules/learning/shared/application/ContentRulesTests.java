package com.sep490.backend.modules.learning.shared.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sep490.backend.common.exception.ContentException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentRulesTests {
  @Test
  void acceptsHttpAndStoredMediaUrls() {
    ContentRules.url("https://cdn.test/image.png");
    ContentRules.url("/media/image.png");
    ContentRules.url(null);
  }

  @Test
  void rejectsUnsafeMediaScheme() {
    assertThatThrownBy(() -> ContentRules.url("javascript:alert(1)"))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("http(s) or /media/");
  }

  @Test
  void rejectsUnsupportedEnumValue() {
    assertThatThrownBy(() -> ContentRules.member("BEGINNER", Set.of("A1", "A2")))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Unsupported value");
  }

  @Test
  void reorderRequiresEveryCurrentIdExactlyOnce() {
    ContentRules.exactOrder(List.of(3L, 1L, 2L), List.of(1L, 2L, 3L));
    assertThatThrownBy(() -> ContentRules.exactOrder(List.of(1L, 1L, 2L), List.of(1L, 2L, 3L)))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("exactly once");
  }
}
