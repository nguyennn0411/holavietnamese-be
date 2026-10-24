package com.sep490.backend.learning.path;

import static com.sep490.backend.learning.shared.ContentStore.*;

import com.sep490.backend.learning.shared.*;
import com.sep490.backend.service.CourseService;
import java.text.Normalizer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningPathService {
  private static final List<String> LEVELS = List.of("A0", "A1", "A2", "B1", "B2", "C1");
  private static final Pattern RANGE = Pattern.compile("(?i)\\b(A0|A1|A2|B1|B2|C1)\\s*[-–/]\\s*(A0|A1|A2|B1|B2|C1)\\b");
  private static final Map<String,List<String>> GOAL_KEYWORDS = Map.of(
      "travel", List.of("travel", "tourism", "transport", "traffic", "direction", "accommodation", "shopping", "food", "drink", "weather", "du lich", "giao thong", "phuong tien", "hoi duong", "cho o", "mua sam", "do an", "thuc uong", "thoi tiet"),
      "connection", List.of("family", "friend", "greeting", "introduction", "conversation", "daily life", "home", "gia dinh", "ban be", "chao hoi", "gioi thieu", "hoi thoai", "cuoc song", "nha o"),
      "work", List.of("work", "workplace", "business", "economy", "occupation", "office", "study", "school", "education", "professional", "cong viec", "noi lam viec", "kinh te", "nghe nghiep", "cong so", "hoc tap", "truong hoc", "giao duc"),
      "culture", List.of("culture", "heritage", "tradition", "festival", "history", "country", "society", "cuisine", "van hoa", "di san", "truyen thong", "le hoi", "lich su", "dat nuoc", "xa hoi", "am thuc"),
      "challenge", List.of("foundation", "comprehensive", "pronunciation", "tone", "grammar", "vocabulary", "skill", "advanced", "nen tang", "toan dien", "phat am", "thanh dieu", "ngu phap", "tu vung", "ky nang", "nang cao"),
      "other", List.of()
  );
  private static final Map<String,String> GOAL_ALIASES = Map.of(
      "daily_communication", "connection",
      "communication", "connection",
      "exam_vsl", "challenge",
      "study", "work"
  );
  private final ContentStore db;
  private final CourseService enrollments;

  @Transactional(readOnly = true)
  public Map<String,Object> me(long user) {
    var preferences = resolvedPreferences(user);
    var recommendation = recommendationFor(user, preferences);
    Long activeId = nullableId(preferences.get("activeCourseId"));
    Map<String,Object> active = activeId == null ? null : course(activeId);
    var activePath = activePath(user);
    if (activePath.isEmpty() && active != null) activePath = List.of(active);
    Map<String,Object> roadmapCourse = active;
    if (roadmapCourse == null && recommendation.get("recommendedCourse") instanceof Map<?,?> candidate)
      roadmapCourse = cast(candidate);

    var result = new LinkedHashMap<String,Object>();
    result.put("preferences", preferences);
    result.put("requiresLevel", preferences.get("level") == null);
    result.put("recommendation", recommendation);
    result.put("activeCourse", active);
    result.put("activePath", activePath);
    result.put("roadmapCourse", roadmapCourse);
    result.put("roadmapPreview", active == null && roadmapCourse != null);
    result.put("continueLearning", continueLearning(user));
    result.put("roadmap", roadmapCourse == null ? List.of() : roadmap(user, id(roadmapCourse,"id")));
    result.put("progress", activePath.isEmpty() ? emptyProgress()
        : pathProgress(user, activePath.stream().map(item -> id(item,"id")).toList()));
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String,Object> recommendation(long user) {
    return recommendationFor(user, resolvedPreferences(user));
  }

  @Transactional
  public Map<String,Object> updatePreferences(long user, LearningPathRequests.Preferences request) {
    ensureRow(user);
    if ((request.level() == null) != (request.levelSource() == null))
      throw new IllegalArgumentException("Trình độ và nguồn xác định trình độ phải được cập nhật cùng nhau");
    String goals = request.learningGoals() == null ? null : db.encode(request.learningGoals().stream()
        .map(String::trim).filter(s -> !s.isBlank()).distinct().toList());
    String source = request.levelSource();
    String requestedLevel = level(request.level());
    Instant placementAt = "PLACEMENT".equals(source)
        ? Objects.requireNonNullElseGet(request.placementTakenAt(), Instant::now)
        : request.placementTakenAt();
    if ("PLACEMENT".equals(source)) {
      var current = db.one("SELECT placement_taken_at FROM learning_path_preferences WHERE user_id=?", user);
      Object storedAt = current.get("placementTakenAt");
      if (storedAt instanceof Timestamp storedTimestamp && placementAt != null
          && storedTimestamp.toInstant().isAfter(placementAt)) {
        source = null;
        requestedLevel = null;
        placementAt = null;
      }
    }
    String confirmed = "USER_CONFIRMED".equals(source) ? requestedLevel : null;
    String placement = "PLACEMENT".equals(source) ? requestedLevel : null;
    String onboarding = "ONBOARDING".equals(source) ? requestedLevel : null;
    db.update("UPDATE learning_path_preferences SET confirmed_level=COALESCE(?,confirmed_level),"
        + " placement_level=COALESCE(?,placement_level),placement_taken_at=COALESCE(?,placement_taken_at),"
        + " onboarding_level=COALESCE(?,onboarding_level),level_source=COALESCE(?,level_source),"
        + " learning_goals=COALESCE(?,learning_goals),daily_study_minutes=COALESCE(?,daily_study_minutes),"
        + " preference_version=preference_version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
        confirmed, placement, placementAt == null ? null : Timestamp.from(placementAt),
        onboarding, source, goals, request.dailyStudyMinutes(), user);
    if (requestedLevel != null && Set.of("USER_CONFIRMED","PROFILE").contains(source))
      db.update("UPDATE users SET target_level=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", requestedLevel, user);
    return me(user);
  }

  @Transactional
  public Map<String,Object> apply(long user, long courseId) {
    return apply(user, List.of(courseId));
  }

  @Transactional
  public Map<String,Object> apply(long user, List<Long> requestedCourseIds) {
    var courseIds = requestedCourseIds == null ? List.<Long>of() : requestedCourseIds.stream()
        .filter(Objects::nonNull).distinct().limit(12).toList();
    if (courseIds.isEmpty()) throw new IllegalArgumentException("Lộ trình phải có ít nhất một khóa học");
    courseIds.forEach(courseId -> {
      if (course(courseId) == null) throw ContentException.missing("COURSE_NOT_PUBLISHED");
    });
    courseIds.forEach(courseId -> enrollments.enroll(user, courseId));
    ensureRow(user);
    var current = db.one("SELECT active_course_id,preference_version FROM learning_path_preferences WHERE user_id=? FOR UPDATE", user);
    Long active = nullableId(current.get("activeCourseId"));
    var storedPath = db.rows("SELECT course_id FROM learning_path_courses WHERE user_id=? ORDER BY position", user)
        .stream().map(row -> ((Number) row.get("courseId")).longValue()).toList();
    if (!storedPath.equals(courseIds)) {
      db.update("DELETE FROM learning_path_courses WHERE user_id=?", user);
      for (int index=0; index<courseIds.size(); index++)
        db.update("INSERT INTO learning_path_courses(user_id,course_id,position) VALUES(?,?,?)",
            user, courseIds.get(index), index + 1);
    }
    long firstCourseId = courseIds.getFirst();
    if (!Objects.equals(active, firstCourseId) || !storedPath.equals(courseIds))
      db.update("UPDATE learning_path_preferences SET active_course_id=?,"
          + " preference_version=preference_version+1,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
          firstCourseId, user);
    return me(user);
  }

  private Map<String,Object> resolvedPreferences(long user) {
    var userRow = db.one("SELECT target_level,learning_goal,daily_learning_goal_minutes FROM users WHERE id=?", user);
    var rows = db.rows("SELECT * FROM learning_path_preferences WHERE user_id=?", user);
    var stored = rows.isEmpty() ? new LinkedHashMap<String,Object>() : new LinkedHashMap<>(rows.getFirst());
    String confirmed = level(stored.get("confirmedLevel"));
    String placement = level(stored.get("placementLevel"));
    String profile = level(userRow.get("targetLevel"));
    String onboarding = level(stored.get("onboardingLevel"));
    String resolved = first(confirmed, placement, profile, onboarding);
    String source = confirmed != null ? "USER_CONFIRMED" : placement != null ? "PLACEMENT"
        : profile != null ? "PROFILE" : onboarding != null ? "ONBOARDING" : null;
    stored.put("level", resolved);
    stored.put("levelSource", source);
    stored.put("learningGoals", goals(stored.get("learningGoals"), userRow.get("learningGoal")));
    Object storedMinutes = stored.get("dailyStudyMinutes");
    stored.put("dailyStudyMinutes", storedMinutes != null ? storedMinutes : userRow.get("dailyLearningGoalMinutes"));
    return stored;
  }

  private Map<String,Object> recommendationFor(long user, Map<String,Object> preferences) {
    String target = (String) preferences.get("level");
    var result = new LinkedHashMap<String,Object>();
    result.put("level", target);
    result.put("levelSource", preferences.get("levelSource"));
    if (target == null) {
      result.put("requiresLevel", true);
      result.put("recommendedCourse", null);
      result.put("recommendedPath", List.of());
      result.put("courses", List.of());
      result.put("nearestCourses", List.of());
      return result;
    }
    var goals = list(preferences.get("learningGoals"));
    var courses = db.rows("SELECT id,code,slug,title,title_vi,description,description_vi,level,thumbnail_url,"
        + "estimated_duration,learning_outcomes,is_free FROM courses WHERE status='PUBLISHED' ORDER BY updated_at DESC,id");
    courses.forEach(c -> score(c,target,goals));
    courses.sort(Comparator.<Map<String,Object>>comparingInt(c -> ((Number)c.get("recommendationScore")).intValue()).reversed()
        .thenComparing(c -> id(c,"id")));
    var exact = courses.stream().filter(c -> bool(c.get("levelMatch"))).toList();
    var recommendedPath = exact.stream().limit(8).toList();
    result.put("requiresLevel", false);
    result.put("recommendedCourse", exact.isEmpty() ? null : exact.getFirst());
    result.put("recommendedPath", recommendedPath);
    result.put("courses", exact);
    result.put("nearestCourses", exact.isEmpty() ? courses.stream().limit(3).toList() : List.of());
    return result;
  }

  private List<Map<String,Object>> activePath(long user) {
    return db.rows("SELECT c.id,c.code,c.slug,c.title,c.title_vi,c.description,c.description_vi,c.level,"
        + "c.thumbnail_url,c.estimated_duration,c.learning_outcomes,c.is_free,p.position "
        + "FROM learning_path_courses p JOIN courses c ON c.id=p.course_id "
        + "WHERE p.user_id=? AND c.status='PUBLISHED' ORDER BY p.position", user);
  }

  private List<Map<String,Object>> continueLearning(long user) {
    var rows = db.rows("SELECT c.id,c.title,c.title_vi,c.description,c.description_vi,c.level,c.thumbnail_url,"
        + "e.id AS enrollment_id,e.status,e.last_accessed_lesson_id,"
        + "(SELECT count(*) FROM lessons l WHERE l.course_id=c.id AND l.published=true) AS total_lessons,"
        + "(SELECT count(*) FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id WHERE p.enrollment_id=e.id"
        + " AND p.status='COMPLETED' AND l.published=true) AS completed_lessons "
        + "FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE e.user_id=?"
        + " AND e.status IN ('ACTIVE','IN_PROGRESS','NOT_STARTED') ORDER BY e.last_accessed_at DESC,e.enrolled_at DESC", user);
    rows.forEach(r -> {
      long total=((Number)r.get("totalLessons")).longValue(), done=((Number)r.get("completedLessons")).longValue();
      r.put("progressPercentage", total == 0 ? 0 : done * 100 / total);
      var next = db.rows("SELECT l.id,l.title,l.title_vi FROM lessons l LEFT JOIN lesson_progress p"
          + " ON p.lesson_id=l.id AND p.enrollment_id=? WHERE l.course_id=? AND l.published=true"
          + " AND (p.status IS NULL OR p.status<>'COMPLETED') ORDER BY l.lesson_order,l.id", r.get("enrollmentId"), r.get("id"));
      r.put("nextLesson", next.isEmpty() ? null : next.getFirst());
    });
    return rows;
  }

  private List<Map<String,Object>> roadmap(long user,long courseId) {
    var enrollment = db.rows("SELECT id FROM enrollments WHERE user_id=? AND course_id=?"
        + " AND status NOT IN ('CANCELLED','DROPPED')", user, courseId);
    Long enrollmentId = enrollment.isEmpty() ? null : id(enrollment.getFirst(),"id");
    var modules = db.rows("SELECT id,title_en AS title,title_vi,description_en AS description,description_vi,sort_order"
        + " FROM course_units WHERE course_id=? AND status='PUBLISHED' ORDER BY sort_order,id", courseId);
    for (var module : modules) {
      var lessons = db.rows("SELECT id,title,title_vi,description,description_vi,estimated_duration,lesson_order"
          + " FROM lessons WHERE course_id=? AND unit_id=? AND published=true ORDER BY unit_sort_order,lesson_order,id",
          courseId, module.get("id"));
      for (var lesson : lessons) {
        String progress = enrollmentId == null ? null : db.rows("SELECT status FROM lesson_progress WHERE enrollment_id=? AND lesson_id=?",
            enrollmentId, lesson.get("id")).stream().findFirst().map(r -> Objects.toString(r.get("status"),null)).orElse(null);
        long unmetPrerequisites = db.count("SELECT count(*) FROM lesson_prerequisites r WHERE r.lesson_id=?"
            + " AND NOT EXISTS (SELECT 1 FROM lesson_progress p JOIN enrollments e ON e.id=p.enrollment_id"
            + " WHERE p.lesson_id=r.prerequisite_lesson_id AND e.user_id=? AND e.course_id=? AND p.status='COMPLETED')",
            lesson.get("id"), user, courseId);
        String state;
        if ("COMPLETED".equals(progress)) state="COMPLETED";
        else if (progress != null) state="IN_PROGRESS";
        else if (unmetPrerequisites == 0) state="AVAILABLE";
        else state="LOCKED";
        lesson.put("state",state);
        lesson.put("unmetPrerequisites", unmetPrerequisites);
      }
      module.put("lessons",lessons);
      boolean completed = !lessons.isEmpty() && lessons.stream().allMatch(l -> "COMPLETED".equals(l.get("state")));
      module.put("milestone", Map.of("type","MODULE_CHECKPOINT","state",completed ? "COMPLETED" : "LOCKED"));
    }
    var courseQuiz = db.rows("SELECT id,title,title_vi,passing_score FROM quizzes WHERE course_id=?"
        + " AND quiz_type='COURSE' AND status='PUBLISHED' ORDER BY id", courseId);
    if (!modules.isEmpty() && !courseQuiz.isEmpty()) {
      var assessment = courseQuiz.getFirst();
      boolean passed = db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=? AND quiz_id=? AND status='SUBMITTED' AND passed=true",
          user, assessment.get("id")) > 0;
      boolean stageCompleted = ((List<?>) modules.getLast().get("lessons")).stream()
          .allMatch(item -> "COMPLETED".equals(((Map<?,?>) item).get("state")));
      String state = passed ? "COMPLETED" : stageCompleted ? "AVAILABLE" : "LOCKED";
      assessment.put("state", state);
      modules.getLast().put("assessment",assessment);
      modules.getLast().put("milestone", Map.of("type","COURSE_ASSESSMENT","state",state));
    }
    return modules;
  }

  private Map<String,Object> pathProgress(long user,List<Long> courseIds) {
    String placeholders=String.join(",",Collections.nCopies(courseIds.size(),"?"));
    Object[] courseArgs=courseIds.toArray();
    long total=db.count("SELECT count(*) FROM lessons WHERE course_id IN ("+placeholders+") AND published=true",courseArgs);
    long done=db.count("SELECT count(*) FROM lesson_progress p JOIN enrollments e ON e.id=p.enrollment_id"
        + " JOIN lessons l ON l.id=p.lesson_id WHERE e.user_id=? AND e.course_id IN ("+placeholders+")"
        + " AND p.status='COMPLETED' AND l.published=true",prepend(user,courseArgs));
    long inProgress=db.count("SELECT count(*) FROM lesson_progress p JOIN enrollments e ON e.id=p.enrollment_id"
        + " JOIN lessons l ON l.id=p.lesson_id WHERE e.user_id=? AND e.course_id IN ("+placeholders+")"
        + " AND p.status<>'COMPLETED' AND l.published=true",prepend(user,courseArgs));
    return Map.of("totalLessons",total,"completedLessons",done,"inProgressLessons",inProgress,
        "percentage",total==0?0:done*100/total);
  }
  private Object[] prepend(Object first,Object[] rest){Object[] values=new Object[rest.length+1];values[0]=first;System.arraycopy(rest,0,values,1,rest.length);return values;}

  private Map<String,Object> emptyProgress(){ return Map.of("totalLessons",0,"completedLessons",0,"inProgressLessons",0,"percentage",0); }
  private Map<String,Object> course(long id){ var r=db.rows("SELECT id,code,slug,title,title_vi,description,description_vi,level,thumbnail_url,estimated_duration,learning_outcomes,is_free FROM courses WHERE id=? AND status='PUBLISHED'",id); return r.isEmpty()?null:r.getFirst(); }
  private void ensureRow(long user){ db.update("INSERT IGNORE INTO learning_path_preferences(user_id) VALUES(?)",user); }
  private void score(Map<String,Object> course,String target,List<String> goals) {
    int[] range=range(course);
    int targetIndex=LEVELS.indexOf(target);
    boolean levelMatch=targetIndex>=range[0]&&targetIndex<=range[1];
    int distance=targetIndex<range[0]?range[0]-targetIndex:targetIndex>range[1]?targetIndex-range[1]:0;
    String searchable=searchableText(course);
    int goalScore=0;
    int matchedGoals=0;
    for (int index=0; index<goals.size(); index++) {
      String goal=normalizeGoal(goals.get(index));
      long keywordMatches=GOAL_KEYWORDS.getOrDefault(goal,List.of()).stream()
          .filter(searchable::contains).limit(4).count();
      if (keywordMatches>0) {
        matchedGoals++;
        int goalWeight=index==0?60:25;
        goalScore += goalWeight + (int)(keywordMatches-1)*8;
      }
    }
    boolean prerequisitesSatisfied=db.count("SELECT count(*) FROM lessons l WHERE l.course_id=? AND l.published=true AND NOT EXISTS (SELECT 1 FROM lesson_prerequisites r WHERE r.lesson_id=l.id)",course.get("id"))>0;
    course.put("levelMin",LEVELS.get(range[0]));
    course.put("levelMax",LEVELS.get(range[1]));
    course.put("levelMatch",levelMatch);
    course.put("goalMatch",matchedGoals>0);
    course.put("matchedLearningGoals",matchedGoals);
    course.put("prerequisitesSatisfied",prerequisitesSatisfied);
    course.put("recommendationScore",1000-distance*100+goalScore-(prerequisitesSatisfied?0:40));
    course.put("recommendationReason",levelMatch?(matchedGoals>0?"LEVEL_AND_GOAL_MATCH":"LEVEL_MATCH"):"NEAREST_LEVEL");
  }
  private String searchableText(Map<String,Object> course) {
    return normalizeSearchText(String.join(" ",
        Objects.toString(course.get("title"),""), Objects.toString(course.get("titleVi"),""),
        Objects.toString(course.get("description"),""), Objects.toString(course.get("descriptionVi"),""),
        Objects.toString(course.get("learningOutcomes"),"")));
  }
  private String normalizeGoal(String goal) {
    String normalized=goal==null?"":goal.trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');
    return GOAL_ALIASES.getOrDefault(normalized,normalized);
  }
  private String normalizeSearchText(String value) {
    String decomposed=Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD);
    return decomposed.replaceAll("\\p{M}+","").replace('đ','d');
  }
  private int[] range(Map<String,Object> c){ String text=Objects.toString(c.get("level"),"")+" "+Objects.toString(c.get("code"),"")+" "+Objects.toString(c.get("title"),""); var m=RANGE.matcher(text); if(m.find()) return ordered(m.group(1).toUpperCase(),m.group(2).toUpperCase()); String l=level(c.get("level")); int i=l==null?0:LEVELS.indexOf(l); return new int[]{i,i}; }
  private int[] ordered(String a,String b){int x=LEVELS.indexOf(a),y=LEVELS.indexOf(b);return new int[]{Math.min(x,y),Math.max(x,y)};}
  private String level(Object v){
    if (v == null) return null;
    String s = v.toString().trim().toUpperCase();
    return LEVELS.contains(s) ? s : null;
  }
  private String first(String... values){return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElse(null);}
  private Long nullableId(Object v){return v instanceof Number n?n.longValue():null;}
  private List<String> goals(Object stored,Object fallback){if(stored!=null){var n=db.json(stored);if(n.isArray()){var out=new ArrayList<String>();n.forEach(x->out.add(x.asText()));return out;}}return fallback==null||fallback.toString().isBlank()?List.of():List.of(fallback.toString());}
  @SuppressWarnings("unchecked") private List<String> list(Object v){return v instanceof List<?> l?l.stream().map(Object::toString).toList():List.of();}
  @SuppressWarnings("unchecked") private Map<String,Object> cast(Map<?,?> v){return (Map<String,Object>)v;}
}
