package com.sep490.backend.dto.courseimport;


import java.util.*;

/** Workbook contract only: no spreadsheet library or database dependencies. */
public enum ImportSheet {
    COURSE("01_COURSE", "courses", "code", null, null,
        "code,level,title_en,title_vi,description_en,description_vi,estimated_hours,status",
        "code,level,title_en,title_vi,estimated_hours,status"),
    UNITS("02_UNITS", "units", "unit_code", COURSE, "course_code",
        "course_code,unit_code,title_en,title_vi,description_en,description_vi,sort_order",
        "course_code,unit_code,title_en,title_vi,sort_order"),
    LESSONS("03_LESSONS", "lessons", "lesson_code", UNITS, "unit_code",
        "unit_code,lesson_code,title_en,title_vi,description_en,description_vi,estimated_minutes,sort_order,status",
        "unit_code,lesson_code,title_en,title_vi,estimated_minutes,sort_order,status"),
    BLOCKS("04_BLOCKS", "blocks", "block_code", LESSONS, "lesson_code",
        "lesson_code,block_code,type,title_en,title_vi,content_en,content_vi,sort_order",
        "lesson_code,block_code,type,title_en,title_vi,sort_order"),
    VOCABULARY("05_VOCABULARY", "vocabulary", "word", LESSONS, "lesson_code",
        "lesson_code,word,meaning_en,meaning_vi,part_of_speech,example_vi,example_en,pronunciation,audio_url,dialect,difficulty",
        "lesson_code,word,meaning_en,meaning_vi"),
    DIALOGUES("06_DIALOGUES", "dialogues", "dialogue_code", LESSONS, "lesson_code",
        "dialogue_code,lesson_code,title_en,title_vi,situation_en,situation_vi",
        "dialogue_code,lesson_code,title_en,title_vi"),
    DIALOGUE_LINES("07_DIALOGUE_LINES", "dialogueLines", "sort_order", DIALOGUES, "dialogue_code",
        "dialogue_code,speaker,text_vi,text_en,sort_order,audio_url",
        "dialogue_code,speaker,text_vi,text_en,sort_order"),
    DRILLS("08_DRILLS", "drills", "sort_order", LESSONS, "lesson_code",
        "lesson_code,vi_text,en_text,grammar_focus,difficulty,sort_order,audio_url",
        "lesson_code,vi_text,en_text,sort_order"),
    EXERCISES("09_EXERCISES", "exercises", "exercise_code", LESSONS, "lesson_code",
        "exercise_code,lesson_code,type,question_vi,question_en,correct_answer,explanation_vi,explanation_en,difficulty,sort_order",
        "exercise_code,lesson_code,type,question_vi,question_en,sort_order"),
    OPTIONS("10_OPTIONS", "options", "option_code", EXERCISES, "exercise_code",
        "exercise_code,option_code,text_vi,text_en,is_correct,sort_order",
        "exercise_code,option_code,text_vi,text_en,is_correct,sort_order");

    public final String sheetName, statistic, key, reference;
    public final ImportSheet parent;
    public final List<String> columns;
    public final Set<String> required;
    ImportSheet(String sheetName, String statistic, String key, ImportSheet parent, String reference,
                String columns, String required) {
        this.sheetName = sheetName; this.statistic = statistic; this.key = key;
        this.parent = parent; this.reference = reference;
        this.columns = List.of(columns.split(",")); this.required = Set.of(required.split(","));
    }
    public boolean scopedKey() {
        return this == VOCABULARY || this == DIALOGUE_LINES || this == DRILLS || this == OPTIONS;
    }
    public static boolean numeric(String column) {
        return Set.of("sort_order", "estimated_minutes", "estimated_hours").contains(column);
    }
    public static int maxLength(String column) {
        if (column.endsWith("_code") || column.equals("code")) return 80;
        if (column.startsWith("title_") || column.equals("word") || column.equals("speaker")) return 200;
        if (column.equals("audio_url")) return 2048;
        if (Set.of("type","status","level","part_of_speech","dialect","difficulty").contains(column)) return 40;
        if (column.equals("pronunciation")) return 200;
        return 10000;
    }
}
