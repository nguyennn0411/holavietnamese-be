package com.sep490.backend.repository;


import com.sep490.backend.dto.courseimport.*;
import com.sep490.backend.repository.CourseImportRepository;
import com.sep490.backend.entity.*;
import com.sep490.backend.entity.enums.CourseStatus;
import jakarta.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import static com.sep490.backend.dto.courseimport.ImportSheet.*;

/** Reuses courses/lessons; separate curricular vocabulary never touches learner-owned vocabulary_entries. */
@Repository
public class CourseImportRepository {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    public CourseImportRepository(JdbcTemplate jdbc, EntityManager em) { this.jdbc = jdbc; this.em = em; }

    private record Mapping(String table, String key, String parentColumn) {}
    private static Mapping mapping(ImportSheet sheet) {
        return switch (sheet) {
            case COURSE -> new Mapping("courses", "code", null);
            case UNITS -> new Mapping("course_units", "code", "course_id");
            case LESSONS -> new Mapping("lessons", "code", "unit_id");
            case BLOCKS -> new Mapping("lesson_blocks", "code", "lesson_id");
            case VOCABULARY -> new Mapping("course_vocabulary", "word", "lesson_id");
            case DIALOGUES -> new Mapping("dialogues", "code", "lesson_id");
            case DIALOGUE_LINES -> new Mapping("dialogue_lines", "sort_order", "dialogue_id");
            case DRILLS -> new Mapping("sentence_drills", "sort_order", "lesson_id");
            case EXERCISES -> new Mapping("exercises", "code", "lesson_id");
            case OPTIONS -> new Mapping("exercise_options", "option_code", "exercise_id");
        };
    }
    private record Existing(long id, String key, Long parentId, String parentCode) {}
    private Existing find(ImportSheet sheet, String key, String parentCode) {
        var m = mapping(sheet);
        String sql = "select t.id, t." + m.key + " as record_key";
        if (sheet.parent == null) sql += " from " + m.table + " t where t." + m.key + "=?";
        else {
            var p = mapping(sheet.parent);
            sql += ", t." + m.parentColumn + " as parent_id, p." + p.key + " as parent_code from " + m.table
                + " t left join " + p.table + " p on p.id=t." + m.parentColumn + " where t." + m.key + "=?";
            if (sheet.scopedKey()) sql += " and p." + p.key + "=?";
        }
        Object lookup = sheet.key.equals("sort_order") ? Integer.valueOf(key) : key;
        Object[] args = sheet.scopedKey() ? new Object[]{lookup, parentCode} : new Object[]{lookup};
        var rows = jdbc.query(sql, (rs, n) -> new Existing(rs.getLong("id"), rs.getString("record_key"),
            sheet.parent == null ? null : rs.getObject("parent_id", Long.class),
            sheet.parent == null ? null : rs.getString("parent_code")), args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void lockCourse(String code) {
        em.createQuery("select c from CourseJpaEntity c where c.code=:code", CourseJpaEntity.class)
            .setParameter("code", code).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }
    public List<CourseImportError> validate(CourseImportCommand command, ImportMode mode) {
        List<CourseImportError> errors = new ArrayList<>();
        var course = find(COURSE, command.courseCode(), null);
        if (mode == ImportMode.CREATE_ONLY && course != null)
            errors.add(command.rows(COURSE).getFirst().error("code", "Course code already exists; use UPDATE_EXISTING."));
        for (var sheet : values()) {
            for (var row : command.rows(sheet)) {
                var existing = find(sheet, row.get(sheet.key), sheet.reference == null ? null : row.get(sheet.reference));
                if (existing == null) continue;
                if (!sheet.key.equals("sort_order") && !existing.key.equals(row.get(sheet.key)))
                    errors.add(row.error(sheet.key, "Code/word differs only by database collation (case/accent); use the exact existing value."));
                if (sheet.parent != null && !Objects.equals(existing.parentCode, row.get(sheet.reference)))
                    errors.add(row.error(sheet.reference, "Existing record belongs to a different parent; moving stable codes is not supported."));
            }
        }
        validateRetainedSortOrders(command, errors);
        // Validate merged option state: omitted options are retained, including their is_correct flag.
        for (var exercise : command.rows(EXERCISES)) {
            if (!Set.of("MULTIPLE_CHOICE", "LISTENING_CHOICE").contains(exercise.get("type"))) continue;
            var existing = find(EXERCISES, exercise.get("exercise_code"), exercise.get("lesson_code"));
            if (existing == null) continue;
            Map<String, Boolean> merged = new HashMap<>();
            jdbc.query("select option_code,is_correct from exercise_options where exercise_id=?", rs -> {
                merged.put(rs.getString(1), rs.getBoolean(2));
            }, existing.id);
            for (var option : command.rows(OPTIONS)) if (option.get("exercise_code").equals(exercise.get("exercise_code")))
                merged.put(option.get("option_code"), Boolean.parseBoolean(option.get("is_correct")));
            if (merged.values().stream().filter(Boolean::booleanValue).count() != 1)
                errors.add(exercise.error("correct_answer", "Retained options would leave multiple correct answers; include old options with is_correct=false."));
        }
        return errors;
    }
    private void validateRetainedSortOrders(CourseImportCommand command, List<CourseImportError> errors) {
        for (var sheet : values()) {
            if (!sheet.columns.contains("sort_order") || sheet.key.equals("sort_order")) continue;
            var m = mapping(sheet);
            String orderColumn = sheet == LESSONS ? "unit_sort_order" : "sort_order";
            var groups = command.rows(sheet).stream().collect(Collectors.groupingBy(row -> row.get(sheet.reference)));
            for (var group : groups.entrySet()) {
                var parent = find(sheet.parent, group.getKey(), null);
                if (parent == null) continue;
                Map<String, Integer> merged = new HashMap<>();
                jdbc.query("select " + m.key + "," + orderColumn + " from " + m.table + " where " + m.parentColumn + "=?", rs -> {
                    merged.put(rs.getString(1), rs.getInt(2));
                }, parent.id);
                for (var row : group.getValue()) merged.put(row.get(sheet.key), Integer.valueOf(row.get("sort_order")));
                for (var row : group.getValue()) {
                    int order = Integer.parseInt(row.get("sort_order"));
                    if (merged.values().stream().filter(v -> v == order).count() > 1)
                        errors.add(row.error("sort_order", "Sort order conflicts with a retained record; include both records to reorder them."));
                }
            }
        }
    }
    public void save(CourseImportCommand command) {
        Map<ImportSheet, Map<String, Long>> ids = new EnumMap<>(ImportSheet.class);
        var row = command.rows(COURSE).getFirst();
        var existing = find(COURSE, row.get("code"), null);
        CourseJpaEntity course = existing == null ? new CourseJpaEntity() : em.find(CourseJpaEntity.class, existing.id);
        course.setCode(row.get("code")); course.setTitle(row.get("title_en")); course.setTitleVi(row.get("title_vi"));
        course.setDescription(row.get("description_en")); course.setDescriptionVi(row.get("description_vi"));
        course.setLevel(row.get("level")); course.setStatus(CourseStatus.valueOf(row.get("status")));
        course.setEstimatedDuration(new BigDecimal(row.get("estimated_hours")).multiply(BigDecimal.valueOf(60)).intValueExact());
        if (existing == null) em.persist(course);
        em.flush();
        ids.put(COURSE, Map.of(row.get("code"), course.getId()));

        for (var sheet : values()) {
            if (sheet == COURSE) continue;
            ids.put(sheet, new HashMap<>());
            for (var data : command.rows(sheet)) {
                long parentId = ids.get(sheet.parent).get(data.get(sheet.reference));
                Existing old = find(sheet, data.get(sheet.key), data.get(sheet.reference));
                long id;
                if (sheet == LESSONS) {
                    LessonJpaEntity lesson = old == null ? new LessonJpaEntity() : em.find(LessonJpaEntity.class, old.id);
                    lesson.setCourse(course); lesson.setUnitId(parentId); lesson.setCode(data.get("lesson_code"));
                    lesson.setTitle(data.get("title_en")); lesson.setTitleVi(data.get("title_vi"));
                    lesson.setDescription(data.get("description_en")); lesson.setDescriptionVi(data.get("description_vi"));
                    lesson.setUnitSortOrder(Integer.valueOf(data.get("sort_order")));
                    lesson.setEstimatedDuration(Integer.parseInt(data.get("estimated_minutes")));
                    lesson.setPublicationStatus(data.get("status")); lesson.setPublished(data.get("status").equals("PUBLISHED"));
                    if (old == null) {
                        int order = jdbc.queryForObject("select coalesce(max(lesson_order),0) from lessons where course_id=?", Integer.class, course.getId());
                        lesson.setLessonOrder(Math.addExact(order, 1)); lesson.setContent(""); em.persist(lesson);
                    }
                    em.flush(); id = lesson.getId();
                } else {
                    var m = mapping(sheet);
                    Map<String, Object> columns = new LinkedHashMap<>();
                    columns.put(m.parentColumn, parentId);
                    for (var column : sheet.columns) {
                        if (column.equals(sheet.reference)) continue;
                        String name = column.equals(sheet.key) ? m.key : column;
                        Object value = data.get(column);
                        if (column.equals("sort_order")) value = Integer.valueOf(data.get(column));
                        if (column.equals("is_correct")) value = Boolean.valueOf(data.get(column));
                        columns.put(name, value);
                    }
                    if (old == null) id = insert(m.table, columns);
                    else {
                        var assignments = columns.keySet().stream().map(c -> c + "=?").collect(Collectors.joining(","));
                        List<Object> parameters = new ArrayList<>(columns.values()); parameters.add(old.id);
                        jdbc.update("update " + m.table + " set " + assignments + " where id=?", parameters.toArray());
                        id = old.id;
                    }
                }
                ids.get(sheet).put(data.get(sheet.key), id);
            }
        }
        reorderLessons(course.getId());
        // JDBC updates must not leave stale JPA entities in the request's persistence context.
        em.clear();
    }
    private long insert(String table, Map<String, Object> columns) {
        String sql = "insert into " + table + " (" + String.join(",", columns.keySet()) + ") values ("
            + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            int i = 1;
            for (Object value : columns.values()) statement.setObject(i++, value);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void reorderLessons(long courseId) {
        var ids = jdbc.queryForList("""
            select l.id from lessons l left join course_units u on u.id=l.unit_id
            where l.course_id=? order by case when u.id is null then 1 else 0 end,
            u.sort_order,u.id,l.unit_sort_order,l.lesson_order,l.id
            """, Long.class, courseId);
        // All normal lesson orders are non-negative. Stage in a disjoint range before reordering,
        // so UNIQUE(course_id,lesson_order) remains valid even when two lessons exchange position.
        jdbc.update("update lessons set lesson_order=-lesson_order-1 where course_id=?", courseId);
        for (int i = 0; i < ids.size(); i++) jdbc.update("update lessons set lesson_order=? where id=?", i + 1, ids.get(i));
    }
}
