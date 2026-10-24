package com.sep490.backend.service.websiteimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/** API contract for the source-faithful website to course pipeline. */
public final class WebsiteImportModel {
  private WebsiteImportModel() {}

  public record AnalyzeRequest(
      @NotBlank String sourceUrl,
      @NotBlank @Pattern(regexp = "SINGLE_LESSON|FULL_COURSE") String mode,
      Long targetCourseId) {}

  public record SaveRequest(
      boolean rightsConfirmed, String license, String attribution) {}

  public record UpdateRequest(List<ModuleDraft> modules) {}

  public record Stats(
      int modules,
      int lessons,
      int blocks,
      int headings,
      int paragraphs,
      int images,
      int audio,
      int video,
      int tables,
      int needsReview) {}

  public record Diagnostic(String level, String code, String message, String sourcePageUrl) {}

  public record MediaProbe(String status, String mimeType, Long contentLength) {}

  public record BlockDraft(
      String key,
      String type,
      int orderIndex,
      String title,
      String html,
      String text,
      String mediaUrl,
      String originalUrl,
      String sourcePageUrl,
      String mimeType,
      Integer width,
      Integer height,
      String altText,
      String caption,
      String attribution,
      String license,
      String importStatus,
      boolean reviewRequired) {}

  public record LessonDraft(
      String key,
      String title,
      String sourcePageUrl,
      boolean selected,
      List<BlockDraft> blocks) {}

  public record ModuleDraft(
      String key, String title, boolean selected, List<LessonDraft> lessons) {}

  public record Project(
      String id,
      String status,
      String mode,
      String sourceUrl,
      String sourceFilename,
      String title,
      String author,
      String license,
      String attribution,
      Long targetCourseId,
      Long resultCourseId,
      boolean rightsConfirmed,
      boolean canSave,
      Stats stats,
      List<ModuleDraft> modules,
      List<Diagnostic> diagnostics,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt) {}
}
