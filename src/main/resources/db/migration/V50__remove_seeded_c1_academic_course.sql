-- V43 historically added a demonstration C1 course to every database.
-- Remove only that seeded course and its dependent learning data.

UPDATE vocabulary_entries
SET lesson_id = NULL
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

UPDATE learning_path_preferences
SET active_course_id = NULL
WHERE active_course_id IN (
    SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
);

DELETE FROM learning_path_courses
WHERE course_id IN (
    SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
);

DELETE FROM quiz_answers
WHERE attempt_id IN (
    SELECT id FROM quiz_attempts
    WHERE quiz_id IN (
        SELECT id FROM quizzes
        WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
        OR lesson_id IN (
            SELECT id FROM lessons WHERE course_id IN (
                SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
            )
        )
    )
);

DELETE FROM quiz_attempts
WHERE quiz_id IN (
    SELECT id FROM quizzes
    WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
    OR lesson_id IN (
        SELECT id FROM lessons WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
    )
);

DELETE FROM activity_progress
WHERE activity_id IN (
    SELECT id FROM lesson_activities
    WHERE lesson_id IN (
        SELECT id FROM lessons WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
    )
);

DELETE FROM activity_grammar_topics
WHERE activity_id IN (
    SELECT id FROM lesson_activities
    WHERE lesson_id IN (
        SELECT id FROM lessons WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
    )
);

DELETE FROM lesson_activities
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM quiz_questions
WHERE quiz_id IN (
    SELECT id FROM quizzes
    WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
    OR lesson_id IN (
        SELECT id FROM lessons WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
    )
);

DELETE FROM quizzes
WHERE course_id IN (
    SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
)
OR lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM lesson_progress
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
)
OR enrollment_id IN (
    SELECT id FROM enrollments WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM enrollments
WHERE course_id IN (
    SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
);

DELETE FROM lesson_prerequisites
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
)
OR prerequisite_lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM dialogue_lines
WHERE dialogue_id IN (
    SELECT id FROM dialogues
    WHERE lesson_id IN (
        SELECT id FROM lessons WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
    )
);

DELETE FROM exercise_options
WHERE exercise_id IN (
    SELECT id FROM exercises
    WHERE lesson_id IN (
        SELECT id FROM lessons WHERE course_id IN (
            SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
        )
    )
);

DELETE FROM lesson_blocks
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM course_vocabulary
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM dialogues
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM sentence_drills
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM exercises
WHERE lesson_id IN (
    SELECT id FROM lessons WHERE course_id IN (
        SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
    )
);

DELETE FROM lessons
WHERE course_id IN (
    SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
);

DELETE FROM course_units
WHERE course_id IN (
    SELECT id FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
);

DELETE FROM courses
WHERE code = 'HOLA-C1-ACADEMIC';
