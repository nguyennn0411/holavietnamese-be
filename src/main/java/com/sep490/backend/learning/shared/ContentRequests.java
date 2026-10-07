package com.sep490.backend.learning.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public final class ContentRequests {
  private ContentRequests() {}

  public record Course(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 200) String slug,
      @NotBlank @Size(max = 200) @JsonProperty("titleEn") @JsonAlias("title") String title,
      @NotBlank @JsonProperty("descriptionEn") @JsonAlias("description") String description,
      @NotBlank String level,
      String thumbnailUrl,
      @Min(0) int estimatedMinutes,
      String learningOutcomes,
      boolean isFree, @Size(max=200) String titleVi, String descriptionVi) {
    public Course(String code, String slug, String title, String description, String level, String thumbnailUrl,
        int estimatedMinutes, String learningOutcomes, boolean isFree) {
      this(code,slug,title,description,level,thumbnailUrl,estimatedMinutes,learningOutcomes,isFree,null,null);
    }
  }

  public record Module(@NotBlank @Size(max = 200) @JsonProperty("titleEn") @JsonAlias("title") String title, @JsonProperty("descriptionEn") @JsonAlias("description") String description, @Size(max=200) String titleVi, String descriptionVi) {
    public Module(String title, String description) { this(title,description,null,null); }
  }

  public record Lesson(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Size(max = 200) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
      @NotBlank @Size(max = 200) @JsonProperty("titleEn") @JsonAlias("title") String title,
      @JsonProperty("descriptionEn") @JsonAlias("description") String description,
      @NotBlank String lessonType,
      @Min(0) int estimatedMinutes,
      List<Long> prerequisiteIds,
      @JsonProperty("learningObjectiveEn") @JsonAlias("learningObjective") String learningObjective, @Size(max=200) String titleVi, String descriptionVi, String learningObjectiveVi) {
    public Lesson(String code, String slug, String title, String description, String lessonType,
        int estimatedMinutes, List<Long> prerequisiteIds, String learningObjective) {
      this(code,slug,title,description,lessonType,estimatedMinutes,prerequisiteIds,learningObjective,null,null,null);
    }
    public Lesson(String code, String slug, @JsonProperty("titleEn") @JsonAlias("title") String title, @JsonProperty("descriptionEn") @JsonAlias("description") String description, String lessonType,
        int estimatedMinutes, List<Long> prerequisiteIds) {
      this(code, slug, title, description, lessonType, estimatedMinutes, prerequisiteIds, null);
    }
  }

  public record Activity(
      @NotBlank String activityType,
      @NotBlank @Size(max = 200) @JsonProperty("titleEn") @JsonAlias("title") String title,
      @JsonProperty("instructionEn") @JsonAlias("instruction") String instruction,
      @NotNull JsonNode contentJson,
      boolean isRequired,
      @NotNull @DecimalMin("0") BigDecimal maxScore,
      String status,
      Long quizId,
      List<Long> grammarTopicIds, @Size(max=200) String titleVi, String instructionVi) {
    public Activity(String activityType, String title, String instruction, JsonNode contentJson, boolean isRequired,
        BigDecimal maxScore, String status, Long quizId, List<Long> grammarTopicIds) {
      this(activityType,title,instruction,contentJson,isRequired,maxScore,status,quizId,grammarTopicIds,null,null);
    }
  }

  public record Status(@NotBlank String status) {}

  public record Reorder(@NotNull List<@NotNull Long> ids) {}

  public record Example(
      @NotBlank @Size(max = 2000) String vietnameseText,
      @NotBlank @Size(max = 2000) String translation,
      @JsonProperty("explanationEn") @JsonAlias("explanation") String explanation) {}

  public record Grammar(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Size(max = 200) String slug,
      @NotBlank @Size(max = 200) @JsonProperty("titleEn") @JsonAlias("title") String title,
      @NotBlank @Size(max = 1000) String structurePattern,
      @NotBlank @JsonProperty("explanationEn") @JsonAlias("explanation") String explanation,
      @JsonProperty("commonMistakesEn") @JsonAlias("commonMistakes") String commonMistakes,
      @NotBlank String level,
      @NotNull List<@Valid Example> examples,
      @JsonProperty("descriptionEn") @JsonAlias("description") String description,
      String notes, @Size(max=200) String titleVi, String descriptionVi, String explanationVi,
      String commonMistakesVi, String structurePatternEn) {
    public Grammar(String code,String slug,String title,String structurePattern,String explanation,
        String commonMistakes,String level,List<Example> examples,String description,String notes) {
      this(code,slug,title,structurePattern,explanation,commonMistakes,level,examples,description,notes,null,null,null,null,null);
    }
    public Grammar(String code, String slug, @JsonProperty("titleEn") @JsonAlias("title") String title, String structurePattern, @JsonProperty("explanationEn") @JsonAlias("explanation") String explanation,
        @JsonProperty("commonMistakesEn") @JsonAlias("commonMistakes") String commonMistakes, String level, List<Example> examples) {
      this(code, slug, title, structurePattern, explanation, commonMistakes, level, examples, null, null);
    }
  }

  public record Question(
      @NotBlank String questionType,
      @NotBlank @JsonProperty("promptEn") @JsonAlias("prompt") String prompt,
      @JsonProperty("explanationEn") @JsonAlias("explanation") String explanation,
      @NotBlank String difficulty,
      @Size(max = 200) String topic,
      String imageUrl,
      String audioUrl,
      String status,
      @NotNull JsonNode correctAnswerJson,
      @NotNull JsonNode options, String promptVi, String explanationVi) {
    public Question(String questionType,String prompt,String explanation,String difficulty,String topic,String imageUrl,
        String audioUrl,String status,JsonNode correctAnswerJson,JsonNode options) {
      this(questionType,prompt,explanation,difficulty,topic,imageUrl,audioUrl,status,correctAnswerJson,options,null,null);
    }
  }

  public record Quiz(
      @NotBlank @Size(max = 200) @JsonProperty("titleEn") @JsonAlias("title") String title,
      @JsonProperty("descriptionEn") @JsonAlias("description") String description,
      @NotBlank String quizType,
      Long courseId,
      Long lessonId,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal passingScore,
      @Min(1) Integer timeLimitMinutes,
      @Min(1) Integer maxAttempts,
      boolean randomizeQuestions, @Size(max=200) String titleVi, String descriptionVi) {
    public Quiz(String title,String description,String quizType,Long courseId,Long lessonId,BigDecimal passingScore,
        Integer timeLimitMinutes,Integer maxAttempts,boolean randomizeQuestions) {
      this(title,description,quizType,courseId,lessonId,passingScore,timeLimitMinutes,maxAttempts,randomizeQuestions,null,null);
    }
  }

  public record QuizQuestion(
      @NotNull Long questionId,
      @NotNull @DecimalMin("0.01") @DecimalMax("10000") BigDecimal points) {}

  public record Answer(@NotNull JsonNode answer) {}
}
