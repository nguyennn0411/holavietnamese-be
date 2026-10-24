package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.learning.path.LearningPathRequests;
import com.sep490.backend.learning.path.LearningPathService;
import com.sep490.backend.learning.shared.ContentStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LearningPathIntegrationTests {
  @Autowired ContentStore db;
  @Autowired LearningPathService paths;

  @Test
  void resolvesLevelPriorityAndRecommendsPublishedCourseRanges() {
    long learner = user(null);
    long ranged = course("A0-A1-" + UUID.randomUUID(), "Vietnamese Foundations A0-A1", "A1", "PUBLISHED");
    long b1Course = course("B1-" + UUID.randomUUID(), "Independent Vietnamese", "B1", "PUBLISHED");
    long b2Course = course("B2-" + UUID.randomUUID(), "Upper Intermediate Vietnamese", "B2", "PUBLISHED");
    long c1Course = course("C1-" + UUID.randomUUID(), "Advanced Vietnamese", "C1", "PUBLISHED");
    course("DRAFT-" + UUID.randomUUID(), "Draft A0", "A0", "DRAFT");

    assertThat(paths.me(learner).get("requiresLevel")).isEqualTo(true);

    Map<String,Object> onboarding = paths.updatePreferences(learner,
        new LearningPathRequests.Preferences("A0", "ONBOARDING", List.of("daily communication"), 15, null));
    assertThat(pref(onboarding, "level")).isEqualTo("A0");
    assertThat(pref(onboarding, "levelSource")).isEqualTo("ONBOARDING");
    assertThat(courseId(onboarding, "recommendation", "recommendedCourse")).isEqualTo(ranged);
    assertThat(pref(paths.me(learner), "level")).isEqualTo("A0");

    Instant latest = Instant.parse("2026-10-24T03:00:00Z");
    Map<String,Object> placement = paths.updatePreferences(learner,
        new LearningPathRequests.Preferences("B1", "PLACEMENT", null, null, latest));
    assertThat(pref(placement, "level")).isEqualTo("B1");
    assertThat(pref(placement, "levelSource")).isEqualTo("PLACEMENT");
    assertThat(courseId(placement, "recommendation", "recommendedCourse")).isEqualTo(b1Course);

    Map<String,Object> stale = paths.updatePreferences(learner,
        new LearningPathRequests.Preferences("A1", "PLACEMENT", null, null, latest.minusSeconds(60)));
    assertThat(pref(stale, "level")).isEqualTo("B1");

    Map<String,Object> confirmed = paths.updatePreferences(learner,
        new LearningPathRequests.Preferences("C1", "USER_CONFIRMED", null, null, null));
    assertThat(pref(confirmed, "level")).isEqualTo("C1");
    assertThat(pref(confirmed, "levelSource")).isEqualTo("USER_CONFIRMED");
    assertThat(courseId(confirmed, "recommendation", "recommendedCourse")).isEqualTo(c1Course);

    long profiledLearner = user("B2");
    Map<String,Object> profileWinsOnboarding = paths.updatePreferences(profiledLearner,
        new LearningPathRequests.Preferences("A1", "ONBOARDING", null, null, null));
    assertThat(pref(profileWinsOnboarding, "level")).isEqualTo("B2");
    assertThat(pref(profileWinsOnboarding, "levelSource")).isEqualTo("PROFILE");
    assertThat(courseId(profileWinsOnboarding, "recommendation", "recommendedCourse")).isEqualTo(b2Course);
    assertThat(pref(paths.me(profiledLearner), "level")).isEqualTo("B2");
  }

  @Test
  void applyingPathIsIdempotentAndPreservesExistingCourseProgress() {
    long learner = user("A1");
    long oldCourse = course("OLD-" + UUID.randomUUID(), "Existing course", "A1", "PUBLISHED");
    long oldModule = module(oldCourse, "OLD-M-" + UUID.randomUUID());
    long oldLesson = lesson(oldCourse, oldModule, "OLD-L-" + UUID.randomUUID(), 1);
    long enrollment = db.insert("INSERT INTO enrollments(user_id,course_id,enrolled_at,status,last_accessed_lesson_id,last_accessed_at)"
        + " VALUES(?,?,CURRENT_TIMESTAMP,'ACTIVE',?,CURRENT_TIMESTAMP)", learner, oldCourse, oldLesson);
    db.insert("INSERT INTO lesson_progress(enrollment_id,lesson_id,status,started_at,completed_at,progress_percent)"
        + " VALUES(?,?,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,100)", enrollment, oldLesson);

    long newCourse = course("NEW-" + UUID.randomUUID(), "Recommended course", "A1", "PUBLISHED");
    long newModule = module(newCourse, "NEW-M-" + UUID.randomUUID());
    lesson(newCourse, newModule, "NEW-L-" + UUID.randomUUID(), 1);
    long nextCourse = course("NEXT-" + UUID.randomUUID(), "Next recommended course", "A1", "PUBLISHED");

    Map<String,Object> first = paths.apply(learner, List.of(newCourse, nextCourse));
    long version = ((Number) pref(first, "preferenceVersion")).longValue();
    Map<String,Object> second = paths.apply(learner, List.of(newCourse, nextCourse));

    assertThat(courseId(second, "activeCourse")).isEqualTo(newCourse);
    assertThat(pref(second, "level")).isEqualTo("A1");
    assertThat(pref(second, "levelSource")).isEqualTo("PROFILE");
    assertThat(((Number) pref(second, "preferenceVersion")).longValue()).isEqualTo(version);
    assertThat(((List<Map<String,Object>>) second.get("activePath"))).extracting("id")
        .containsExactly(newCourse, nextCourse);
    assertThat(db.count("SELECT count(*) FROM enrollments WHERE id=? AND user_id=?", enrollment, learner)).isEqualTo(1);
    assertThat(db.count("SELECT count(*) FROM lesson_progress WHERE enrollment_id=? AND status='COMPLETED'", enrollment)).isEqualTo(1);
    assertThat(((List<Map<String,Object>>) second.get("continueLearning")))
        .anyMatch(item -> ((Number) item.get("id")).longValue() == oldCourse);

    Map<String,Object> refreshed = paths.me(learner);
    assertThat(courseId(refreshed, "activeCourse")).isEqualTo(newCourse);
    assertThat(((List<Map<String,Object>>) refreshed.get("activePath"))).extracting("id")
        .containsExactly(newCourse, nextCourse);
    assertThat(((Number) ((Map<?,?>) refreshed.get("progress")).get("completedLessons")).longValue()).isZero();
    assertThat(db.count("SELECT count(*) FROM lesson_progress WHERE enrollment_id=? AND status='COMPLETED'", enrollment)).isEqualTo(1);
    assertThat(((List<Map<String,Object>>) refreshed.get("continueLearning")))
        .anyMatch(item -> ((Number) item.get("id")).longValue() == oldCourse);

    Map<String,Object> otherLearner = paths.me(user("A1"));
    assertThat(otherLearner.get("activeCourse")).isNull();
    assertThat((List<?>) otherLearner.get("continueLearning")).isEmpty();
  }

  @Test
  void ranksSameLevelCoursesByPrimaryAndSecondaryOnboardingGoals() {
    long learner = user(null);
    long generic = course("GENERIC-" + UUID.randomUUID(), "Vietnamese A2 foundations", "A2", "PUBLISHED");
    long travel = course("TRAVEL-" + UUID.randomUUID(), "Giao thông và phương tiện", "A2", "PUBLISHED");
    long work = course("WORK-" + UUID.randomUUID(), "School, study and occupations", "A2", "PUBLISHED");

    Map<String,Object> travelFirst = paths.updatePreferences(learner,
        new LearningPathRequests.Preferences("A2", "ONBOARDING", List.of("travel", "work"), 15, null));
    assertThat(courseId(travelFirst, "recommendation", "recommendedCourse")).isEqualTo(travel);
    assertThat(recommendationCourse(travelFirst).get("recommendationReason")).isEqualTo("LEVEL_AND_GOAL_MATCH");
    assertThat((List<Map<String,Object>>) cast(travelFirst.get("recommendation")).get("recommendedPath"))
        .extracting("id").contains(travel, work, generic);

    Map<String,Object> workFirst = paths.updatePreferences(learner,
        new LearningPathRequests.Preferences(null, null, List.of("work", "travel"), null, null));
    assertThat(courseId(workFirst, "recommendation", "recommendedCourse")).isEqualTo(work);
    assertThat(courseId(workFirst, "recommendation", "recommendedCourse")).isNotEqualTo(generic);
  }

  @Test
  void returnsAnEmptyExactMatchWithExplainedNearestOptions() {
    db.update("UPDATE courses SET status='DRAFT' WHERE status='PUBLISHED'");
    long nearest = course("NEAREST-A1-" + UUID.randomUUID(), "Nearest beginner course", "A1", "PUBLISHED");
    long learner = user("C1");

    Map<String,Object> result = paths.me(learner);
    Map<String,Object> recommendation = cast(result.get("recommendation"));

    assertThat(recommendation.get("recommendedCourse")).isNull();
    assertThat((List<?>) recommendation.get("courses")).isEmpty();
    assertThat((List<Map<String,Object>>) recommendation.get("nearestCourses"))
        .singleElement()
        .satisfies(option -> {
          assertThat(((Number) option.get("id")).longValue()).isEqualTo(nearest);
          assertThat(option.get("recommendationReason")).isEqualTo("NEAREST_LEVEL");
        });
  }

  private long user(String level) {
    return db.insert("INSERT INTO users(email,password_hash,enabled,target_level) VALUES(?,'test',true,?)",
        UUID.randomUUID() + "@learning-path.test", level);
  }

  private long course(String code, String title, String level, String status) {
    return db.insert("INSERT INTO courses(code,slug,title,description,level,estimated_duration,status) VALUES(?,?,?,?,?,60,?)",
        code, code.toLowerCase(), title, title + " description", level, status);
  }

  private long module(long course, String code) {
    return db.insert("INSERT INTO course_units(course_id,code,title_en,title_vi,description_en,description_vi,sort_order,status)"
        + " VALUES(?,?,?,'Giai đoạn','Description','Mô tả',1,'PUBLISHED')", course, code, "Stage");
  }

  private long lesson(long course, long module, String code, int order) {
    return db.insert("INSERT INTO lessons(course_id,unit_id,code,title,title_vi,description,lesson_order,unit_sort_order,content,estimated_duration,published)"
        + " VALUES(?,?,?,'Lesson','Bài học','Real published lesson',?,?, '{}',10,true)", course, module, code, order, order);
  }

  @SuppressWarnings("unchecked")
  private Object pref(Map<String,Object> result, String key) {
    return ((Map<String,Object>) result.get("preferences")).get(key);
  }

  @SuppressWarnings("unchecked")
  private long courseId(Map<String,Object> result, String first, String second) {
    Map<String,Object> nested = (Map<String,Object>) result.get(first);
    return ((Number) ((Map<String,Object>) nested.get(second)).get("id")).longValue();
  }

  @SuppressWarnings("unchecked")
  private long courseId(Map<String,Object> result, String key) {
    return ((Number) ((Map<String,Object>) result.get(key)).get("id")).longValue();
  }

  @SuppressWarnings("unchecked")
  private Map<String,Object> recommendationCourse(Map<String,Object> result) {
    return (Map<String,Object>) ((Map<String,Object>) result.get("recommendation")).get("recommendedCourse");
  }

  @SuppressWarnings("unchecked")
  private Map<String,Object> cast(Object value) {
    return (Map<String,Object>) value;
  }
}
