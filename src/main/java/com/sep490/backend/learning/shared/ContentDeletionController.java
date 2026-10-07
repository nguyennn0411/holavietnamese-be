package com.sep490.backend.learning.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Delete unused content; assessed content must be archived to preserve learner history. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class ContentDeletionController {
    private final ContentStore db;

    private void requireEmpty(String sql, long id) {
        if (db.count(sql, id) > 0) throw ContentException.conflict("CONTENT_IN_USE",
            "This content has learning history or linked content. Archive it instead, or remove its links first.");
    }

    @DeleteMapping("/quizzes/{id}")
    @Transactional
    public void quiz(@PathVariable long id) {
        db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", id);
        requireEmpty("SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM lesson_activities WHERE quiz_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM courses WHERE final_assessment_quiz_id=?", id);
        db.update("DELETE FROM quiz_questions WHERE quiz_id=?", id);
        db.update("DELETE FROM quizzes WHERE id=?", id);
    }

    @DeleteMapping("/lessons/{id}")
    @Transactional
    public void lesson(@PathVariable long id) {
        var lesson = db.one("SELECT course_id FROM lessons WHERE id=?", id);
        db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", lesson.get("courseId"));
        requireEmpty("SELECT COUNT(*) FROM lesson_progress WHERE lesson_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM enrollments WHERE last_accessed_lesson_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM quizzes WHERE lesson_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM vocabulary_entries WHERE lesson_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM lesson_prerequisites WHERE prerequisite_lesson_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM activity_progress p JOIN lesson_activities a ON a.id=p.activity_id WHERE a.lesson_id=?", id);
        db.update("DELETE FROM activity_grammar_topics WHERE activity_id IN (SELECT id FROM lesson_activities WHERE lesson_id=?)", id);
        db.update("DELETE FROM lesson_activities WHERE lesson_id=?", id);
        db.update("DELETE FROM lesson_prerequisites WHERE lesson_id=?", id);
        db.update("DELETE FROM exercise_options WHERE exercise_id IN (SELECT id FROM exercises WHERE lesson_id=?)", id);
        db.update("DELETE FROM dialogue_lines WHERE dialogue_id IN (SELECT id FROM dialogues WHERE lesson_id=?)", id);
        for (String table : java.util.List.of("exercises", "dialogues", "sentence_drills", "course_vocabulary", "lesson_blocks"))
            db.update("DELETE FROM " + table + " WHERE lesson_id=?", id);
        db.update("DELETE FROM lessons WHERE id=?", id);
    }

    @DeleteMapping("/courses/{id}")
    @Transactional
    public void course(@PathVariable long id) {
        db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", id);
        requireEmpty("SELECT COUNT(*) FROM enrollments WHERE course_id=?", id);
        requireEmpty("SELECT COUNT(*) FROM quizzes WHERE course_id=?", id);
        db.update("DELETE FROM lesson_prerequisites WHERE lesson_id IN (SELECT id FROM lessons WHERE course_id=?)", id);
        for (var row : db.rows("SELECT id FROM lessons WHERE course_id=?", id)) lesson(ContentStore.id(row, "id"));
        db.update("DELETE FROM course_units WHERE course_id=?", id);
        db.update("DELETE FROM courses WHERE id=?", id);
    }
}
