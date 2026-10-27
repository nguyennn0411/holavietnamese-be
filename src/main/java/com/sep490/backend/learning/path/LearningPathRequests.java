package com.sep490.backend.learning.path;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class LearningPathRequests {
  private LearningPathRequests() {}

  public record Preferences(
      @Pattern(regexp = "A0|A1|A2|B1|B2|C1") String level,
      @Pattern(regexp = "USER_CONFIRMED|PLACEMENT|PROFILE|ONBOARDING") String levelSource,
      List<@NotBlank @Size(max = 80) String> learningGoals,
      @Min(5) @Max(240) Integer dailyStudyMinutes,
      Instant placementTakenAt) {}

  public record Apply(Long courseId, List<@NotNull Long> courseIds) {}
}
