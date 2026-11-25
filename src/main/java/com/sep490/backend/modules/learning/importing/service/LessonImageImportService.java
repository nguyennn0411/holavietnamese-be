package com.sep490.backend.modules.learning.importing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.common.config.CloudinaryProperties;
import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.learning.authoring.application.CourseContentService;
import com.sep490.backend.modules.learning.authoring.application.CourseReviewService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonImageImportService {
  private static final Pattern IMAGE_NAME = Pattern.compile("(?i).+\\.(png|jpe?g|webp)");
  private static final Set<String> PRACTICE_TYPES = Set.of("PRACTICE", "MULTIPLE_CHOICE",
      "FILL_BLANK", "MATCHING", "REORDER_SENTENCE", "LISTEN_AND_CHOOSE", "LISTEN_AND_TYPE",
      "LISTENING", "TRANSLATION");
  private static final Set<String> CONTENT_TYPES = Set.of("TEXT", "VOCABULARY", "FLASHCARD",
      "GRAMMAR", "DIALOGUE", "AUDIO", "VIDEO", "SOUND_PATTERN");

  private final ContentStore db;
  private final CourseContentService courses;
  private final CourseReviewService workflow;
  private final CloudinaryProperties cloudinary;

  public enum Action { ADD, MOVE, SKIP, UNKNOWN_LESSON, CHOOSE_ACTIVITY, AMBIGUOUS }

  public record Item(@NotBlank @Size(max = 200) String lessonKey,
                     @NotBlank @Size(max = 255) String fileName,
                     @Size(max = 200) String assetType,
                     @Positive Long activityId) {}

  public record ActivityOption(long id, String title, String titleVi) {}

  public record Target(Action action, String lessonTitle, String lessonTitleVi,
                       Long activityId, List<ActivityOption> activityOptions) {}

  public record Image(@NotBlank @Size(max = 200) String lessonKey,
                      @NotBlank @Size(max = 255) String fileName,
                      @NotNull @Positive Long activityId,
                      @Size(max = 1000) String imageUrl,
                      @NotBlank @Size(max = 200) String title,
                      @NotBlank @Size(max = 200) String titleVi) {}

  private record ActivityRow(long id, String type, String status, String title, String titleVi,
                             boolean required, ObjectNode content) {
    boolean eligible() {
      return !"ARCHIVED".equals(status) && (CONTENT_TYPES.contains(type) || PRACTICE_TYPES.contains(type));
    }

    ActivityOption option() {
      return new ActivityOption(id, title, titleVi);
    }
  }

  private record Match(Action action, ActivityRow target, ActivityRow source) {}

  @Transactional(readOnly = true)
  public List<Target> preview(long courseId, List<Item> items, LearnerPrincipal actor) {
    workflow.assertCanEdit(courseId, actor);
    var lessons = lessonIndex(courseId);
    Map<Long, List<ActivityRow>> byLesson = new HashMap<>();
    return items.stream().map(item -> {
      String fileName = validateFileName(item.fileName());
      var lesson = lessons.get(normalize(item.lessonKey()));
      if (lesson == null) return new Target(Action.UNKNOWN_LESSON, null, null, null, List.of());
      var existing = byLesson.computeIfAbsent(ContentStore.id(lesson, "id"), this::lessonActivities);
      var match = match(fileName, existing, item.activityId(), item.assetType());
      return new Target(match.action(), (String) lesson.get("title"), (String) lesson.get("titleVi"),
          match.target() == null ? null : match.target().id(),
          existing.stream().filter(ActivityRow::eligible).map(ActivityRow::option).toList());
    }).toList();
  }

  @Transactional
  public Action attach(long courseId, Image image, LearnerPrincipal actor) {
    courses.lock(courseId);
    workflow.assertCanEdit(courseId, actor);
    String fileName = validateFileName(image.fileName());
    var lesson = lessonIndex(courseId).get(normalize(image.lessonKey()));
    if (lesson == null) throw ContentException.invalid("Unknown lesson code in this course.");
    var match = match(fileName, lessonActivities(ContentStore.id(lesson, "id")), image.activityId(), null);
    if (match.action() == Action.SKIP) return Action.SKIP;
    if (match.action() != Action.ADD && match.action() != Action.MOVE) {
      throw ContentException.conflict("LESSON_IMAGE_TARGET_REQUIRED", "Select an existing content activity in this lesson.");
    }
    var source = match.source();
    String url = source == null ? image.imageUrl() : source.content().path("imageUrl").asText();
    validateCloudinaryUrl(url);
    var target = match.target();
    var content = target.content().deepCopy();
    JsonNode current = content.path("illustrations");
    if (!current.isMissingNode() && !current.isNull() && !current.isArray()) {
      throw ContentException.invalid("Activity illustrations must be an array.");
    }
    var illustrations = current instanceof ArrayNode array ? array : content.putArray("illustrations");
    illustrations.addObject().put("imageUrl", url).put("captionEn", image.title())
        .put("captionVi", image.titleVi()).put("placement", "inline").put("importImageFile", fileName);
    String encoded = db.encode(content);
    if (encoded.length() > 100000) throw ContentException.invalid("Activity content must be a JSON object under 100 KB.");
    db.update("UPDATE lesson_activities SET content_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", encoded, target.id());
    if (source != null && !source.required() && !"ARCHIVED".equals(source.status())) {
      // Keep the legacy row for references and progress.
      var archived = source.content().deepCopy().put("inlineTargetActivityId", target.id());
      db.update("UPDATE lesson_activities SET status='ARCHIVED',content_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
          db.encode(archived), source.id());
    }
    return match.action();
  }

  private Map<String, Map<String, Object>> lessonIndex(long courseId) {
    Map<String, Map<String, Object>> lessons = new HashMap<>();
    for (var lesson : db.rows("SELECT id,code,title,title_vi FROM lessons WHERE course_id=?", courseId)) {
      if (lesson.get("code") instanceof String code) lessons.put(normalize(code), lesson);
    }
    return lessons;
  }

  private List<ActivityRow> lessonActivities(long lessonId) {
    List<ActivityRow> result = new ArrayList<>();
    for (var row : db.rows("""
        SELECT id,activity_type,status,title,title_vi,is_required,content_json
        FROM lesson_activities WHERE lesson_id=? ORDER BY order_index,id
        """, lessonId)) {
      if (db.json(row.get("contentJson")) instanceof ObjectNode object) {
        result.add(new ActivityRow(ContentStore.id(row, "id"), (String) row.get("activityType"),
            (String) row.get("status"), (String) row.get("title"), (String) row.get("titleVi"),
            ContentStore.bool(row.get("isRequired")), object));
      }
    }
    return result;
  }

  private Match match(String fileName, List<ActivityRow> rows, Long activityId, String assetType) {
    var eligible = rows.stream().filter(ActivityRow::eligible).toList();
    for (var row : eligible) {
      for (var image : row.content().path("illustrations")) {
        if ("inline".equals(image.path("placement").asText()) && !image.path("imageUrl").asText().isBlank()
            && fileName.equals(normalize(image.path("importImageFile").asText("")))) {
          return new Match(Action.SKIP, row, null);
        }
      }
    }
    var sources = rows.stream().filter(row -> "IMAGE".equals(row.type())
        && !row.content().path("imageUrl").asText().isBlank()
        && fileName.equals(normalize(row.content().path("importImageFile").asText("")))).toList();
    if (sources.size() > 1) return new Match(Action.AMBIGUOUS, null, null);
    var source = sources.isEmpty() ? null : sources.getFirst();
    var target = activityId == null ? suggestedActivity(assetType, eligible)
        : eligible.stream().filter(row -> row.id() == activityId).findFirst().orElse(null);
    if (target == null) return new Match(Action.CHOOSE_ACTIVITY, null, source);
    return new Match(source == null ? Action.ADD : Action.MOVE, target, source);
  }

  private ActivityRow suggestedActivity(String assetType, List<ActivityRow> rows) {
    if (rows.size() == 1) return rows.getFirst();
    if (assetType == null || rows.isEmpty()) return null;
    return switch (normalize(assetType)) {
      case "01-knowledge" -> rows.stream().filter(row -> "TEXT".equals(row.type()) || "GRAMMAR".equals(row.type()))
          .findFirst().orElse(null);
      case "02-vocabulary", "04-picture-cards" -> rows.stream().filter(row -> "VOCABULARY".equals(row.type())
          || "FLASHCARD".equals(row.type()) || normalize(row.title()).matches(".*(vocabulary|everyday words|useful examples|meaning changes).*")
          || normalize(row.titleVi()).contains("từ vựng")).findFirst().orElse(null);
      case "03-practice" -> rows.stream().filter(row -> PRACTICE_TYPES.contains(row.type())
          || normalize(row.title()).matches("^(read|reading|practice|practise|write|writing)( .*)?$"))
          .findFirst().orElse(null);
      default -> null;
    };
  }

  private static String validateFileName(String value) {
    String name = normalize(value);
    if (!IMAGE_NAME.matcher(name).matches() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
        || name.chars().anyMatch(Character::isISOControl)) {
      throw ContentException.invalid("Invalid lesson image file name.");
    }
    return name;
  }

  private void validateCloudinaryUrl(String value) {
    String cloudName = cloudinary.getCloudName();
    try {
      URI uri = URI.create(value == null ? "" : value);
      if (cloudName != null && !cloudName.isBlank()
          && "https".equals(uri.getScheme()) && "res.cloudinary.com".equalsIgnoreCase(uri.getHost())
          && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null
          && uri.getFragment() == null && uri.getPath().startsWith("/" + cloudName + "/image/upload/")) return;
    } catch (IllegalArgumentException ignored) {
      // Use the same validation error for malformed and mismatched URLs.
    }
    throw ContentException.invalid("Expected a Cloudinary image URL for this site.");
  }

  private static String normalize(String value) {
    return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }
}
