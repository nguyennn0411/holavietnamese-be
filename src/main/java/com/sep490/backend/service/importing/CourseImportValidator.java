package com.sep490.backend.service.importing;

import com.sep490.backend.dto.courseimport.*;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.sep490.backend.dto.courseimport.ImportSheet.*;

@Component
public class CourseImportValidator {
    private static final Set<String> LEVELS = Set.of("STARTER", "A1", "A2", "B1", "B2", "C1", "C2");
    private static final Set<String> STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> BLOCK_TYPES = Set.of("INTRO","DIALOGUE","VOCABULARY","PRONUNCIATION","GRAMMAR","LISTENING","SPEAKING","READING","WRITING","CULTURE","SENTENCE_DRILL","EXERCISE","QUIZ");
    private static final Set<String> EXERCISE_TYPES = Set.of("MULTIPLE_CHOICE","TRUE_FALSE","FILL_IN_BLANK","MATCHING","ORDER_SENTENCE","LISTENING_CHOICE","SHORT_ANSWER");
    public List<CourseImportError> validate(CourseImportCommand command) {
        var errors = new ArrayList<>(command.errors());
        if (command.rows(COURSE).size() != 1)
            errors.add(new CourseImportError(COURSE.sheetName, 2, "code", "", "Workbook must contain exactly one course."));
        for (var sheet : values()) {
            Set<String> keys = new HashSet<>(), orders = new HashSet<>();
            Set<String> parents = new HashSet<>();
            if (sheet.parent != null) for (var p : command.rows(sheet.parent)) parents.add(p.get(sheet.parent.key));
            for (var row : command.rows(sheet)) {
                for (String column : sheet.columns) {
                    String value = row.get(column);
                    if (sheet.required.contains(column) && value.isBlank()) errors.add(row.error(column, "Required value is blank."));
                    if (value.length() > maxLength(column)) errors.add(row.error(column, "Maximum length is " + maxLength(column) + "."));
                    if (value.indexOf('\u001f') >= 0 || value.indexOf('\u0000') >= 0) errors.add(row.error(column, "Control characters are not allowed."));
                    if ((column.equals("code") || column.endsWith("_code")) && !value.isEmpty() && !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}"))
                        errors.add(row.error(column, "Codes must use ASCII letters, digits, dot, underscore or hyphen."));
                    if (numeric(column) && !value.isBlank()) {
                        try {
                            BigDecimal number = new BigDecimal(value);
                            if (number.signum() < 0 || number.compareTo(new BigDecimal("1000000")) > 0) throw new ArithmeticException();
                            if (!column.equals("estimated_hours")) {
                                if (!value.matches("[0-9]+")) throw new ArithmeticException();
                                number.intValueExact();
                            }
                            else number.multiply(BigDecimal.valueOf(60)).intValueExact();
                        } catch (NumberFormatException | ArithmeticException e) {
                            errors.add(row.error(column, "Expected a non-negative number up to 1,000,000; orders/minutes must be integers and hours must resolve to whole minutes."));
                        }
                    }
                }
                // Case-fold keys to avoid ambiguous workbooks on case-insensitive SQL Server/MySQL collations.
                if (!keys.add(row.key().toLowerCase(Locale.ROOT))) errors.add(row.error(sheet.key, "Duplicate key in this sheet."));
                if (sheet.parent != null && !parents.contains(row.get(sheet.reference)))
                    errors.add(row.error(sheet.reference, "Referenced " + sheet.parent.key + " does not exist in " + sheet.parent.sheetName + "."));
                if (sheet.columns.contains("sort_order")) {
                    String order = row.get("sort_order");
                    try { order = new BigDecimal(order).stripTrailingZeros().toPlainString(); } catch (NumberFormatException ignored) { }
                    if (!orders.add(row.get(sheet.reference) + "\u001f" + order)) errors.add(row.error("sort_order", "Duplicate sort order within the same parent."));
                }
                if (sheet == COURSE) allowed(row, "level", LEVELS, errors);
                if (sheet == COURSE || sheet == LESSONS) allowed(row, "status", STATUS, errors);
                if (sheet == BLOCKS) allowed(row, "type", BLOCK_TYPES, errors);
                if (sheet == EXERCISES) {
                    allowed(row, "type", EXERCISE_TYPES, errors);
                    if (!row.get("type").equals("SHORT_ANSWER") && row.get("correct_answer").isBlank())
                        errors.add(row.error("correct_answer", "Objective exercises require a correct answer."));
                    if (row.get("type").equals("TRUE_FALSE") && !Set.of("true", "false").contains(row.get("correct_answer")))
                        errors.add(row.error("correct_answer", "TRUE_FALSE answer must be true or false."));
                    if (Set.of("MULTIPLE_CHOICE", "LISTENING_CHOICE").contains(row.get("type"))) {
                        var options = command.rows(OPTIONS).stream().filter(o -> o.get("exercise_code").equals(row.get("exercise_code"))).toList();
                        if (options.isEmpty()) errors.add(row.error("exercise_code", "Choice exercises must include options in 10_OPTIONS."));
                        long correct = options.stream().filter(o -> o.get("is_correct").equalsIgnoreCase("true")).count();
                        if (correct != 1) errors.add(row.error("correct_answer", "Choice exercises require exactly one correct option."));
                        if (options.stream().noneMatch(o -> o.get("option_code").equals(row.get("correct_answer")) && o.get("is_correct").equalsIgnoreCase("true")))
                            errors.add(row.error("correct_answer", "Correct answer must be the option_code of the correct option."));
                    }
                }
                if (sheet == OPTIONS && !Set.of("true","false").contains(row.get("is_correct").toLowerCase(Locale.ROOT)))
                    errors.add(row.error("is_correct", "Expected true or false."));
                if (sheet.columns.contains("difficulty") && !row.get("difficulty").isBlank())
                    allowed(row, "difficulty", Set.of("EASY", "MEDIUM", "HARD"), errors);
            }
        }
        return errors;
    }
    private static void allowed(CourseImportCommand.Row row, String column, Set<String> values, List<CourseImportError> errors) {
        if (!values.contains(row.get(column))) errors.add(row.error(column, "Expected one of: " + String.join(", ", new TreeSet<>(values)) + "."));
    }
}
