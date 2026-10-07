package com.sep490.backend;
import com.sep490.backend.exception.CourseImportRejected;

import com.sep490.backend.repository.CourseImportRepository;
import com.sep490.backend.entity.User;
import com.sep490.backend.repository.RoleRepository;
import com.sep490.backend.repository.jpa.UserJpaRepository;
import com.sep490.backend.dto.courseimport.*;
import com.sep490.backend.service.*;
import com.sep490.backend.config.LearnerPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static com.sep490.backend.CourseImportWorkbookFixture.*;
import static com.sep490.backend.dto.courseimport.ImportSheet.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CourseImportIntegrationTests {
    @Autowired CourseImportService validator;
    @Autowired CourseImportService importer;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired UserJpaRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean CourseImportRepository persistence;

    @BeforeEach void cleanIsolatedDatabase() throws Exception {
        try (var c = jdbc.getDataSource().getConnection()) {
            String url = c.getMetaData().getURL();
            boolean sqlServerTest = c.getMetaData().getDatabaseProductName().equals("Microsoft SQL Server") && "hola_features_test".equals(c.getCatalog());
            if (!url.startsWith("jdbc:h2:mem:hola-tests") && !url.matches("jdbc:mysql://[^/]+/hola_features_test(\\?.*)?") && !sqlServerTest)
                throw new IllegalStateException("Refusing to modify a non-test database.");
        }
        for (String table : List.of("exercise_options","exercises","sentence_drills","dialogue_lines","dialogues",
                "course_vocabulary","lesson_blocks","vocabulary_entries","lesson_progress","enrollments","lessons","course_units","courses","users"))
            jdbc.update("delete from " + table);
    }
    private CourseImportPreview preview(byte[] bytes) { return validator.validate(bytes, "course.xlsx", ImportMode.CREATE_ONLY); }
    private CourseImportPreview create(byte[] bytes) { return importer.importCourse(bytes, "course.xlsx", ImportMode.CREATE_ONLY); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "course.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }
    @Test void previewValidWorkbookDoesNotWrite() throws Exception {
        var result = preview(workbook(w -> {}));
        assertThat(result.errors()).isEmpty(); assertThat(result.success()).isTrue();
        assertThat(result.statistics()).containsEntry("courses",1).containsEntry("options",2).containsEntry("vocabulary",1);
        assertThat(count("courses")).isZero(); assertThat(count("lessons")).isZero();
    }
    @Test void importsEverySheetAndPreservesVietnamese() throws Exception {
        assertThat(create(workbook(w -> {})).success()).isTrue();
        for (String table : List.of("courses","course_units","lessons","lesson_blocks","course_vocabulary","dialogues","dialogue_lines","sentence_drills","exercises"))
            assertThat(count(table)).as(table).isEqualTo(1);
        assertThat(count("exercise_options")).isEqualTo(2);
        assertThat(count("vocabulary_entries")).isZero();
        assertThat(jdbc.queryForObject("select title_vi from courses",String.class)).isEqualTo("Tiếng Việt A1");
        assertThat(jdbc.queryForObject("select content_vi from lesson_blocks",String.class)).isEqualTo("Xin chào Việt Nam");
        assertThat(jdbc.queryForObject("select word from course_vocabulary",String.class)).isEqualTo("chào");
        assertThat(jdbc.queryForObject("select estimated_duration from courses",Integer.class)).isEqualTo(150);
    }
    @Test void missingSheetAndColumnHaveStructuredErrors() throws Exception {
        var result = preview(workbook(w -> {
            w.removeSheetAt(w.getSheetIndex("07_DIALOGUE_LINES"));
            w.getSheet("01_COURSE").getRow(0).getCell(1).setCellValue("wrong_header");
        }));
        assertThat(result.errors()).anySatisfy(e -> { assertThat(e.sheet()).isEqualTo("07_DIALOGUE_LINES"); assertThat(e.row()).isEqualTo(1); });
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.column()).isEqualTo("level"));
        assertThat(result.warnings()).isNotEmpty();
        assertThat(count("courses")).isZero();
    }
    @Test void invalidReferenceHasExactLocationAndImportDoesNotWrite() throws Exception {
        byte[] bytes = workbook(w -> set(w, LESSONS, 2, "unit_code", "A1-U99"));
        assertThat(preview(bytes).errors()).anySatisfy(e -> {
            assertThat(e.sheet()).isEqualTo("03_LESSONS"); assertThat(e.row()).isEqualTo(2);
            assertThat(e.column()).isEqualTo("unit_code"); assertThat(e.value()).isEqualTo("A1-U99");
        });
        assertThatThrownBy(() -> create(bytes)).isInstanceOf(CourseImportRejected.class);
        assertThat(count("courses")).isZero();
    }
    @Test void duplicateLessonCodeRejected() throws Exception {
        var result = preview(workbook(w -> add(w, LESSONS,"A1-U1,A1-L1,Second,Hai,Desc,Mô tả,20,2,DRAFT")));
        assertThat(result.errors()).anySatisfy(e -> { assertThat(e.column()).isEqualTo("lesson_code"); assertThat(e.row()).isEqualTo(3); });
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","NaN","1000001","1e1"})
    void invalidIntegerOrSortOrderRejected(String value) throws Exception {
        assertThat(preview(workbook(w -> set(w, LESSONS, 2,"sort_order",value))).errors())
            .anySatisfy(e -> assertThat(e.column()).isEqualTo("sort_order"));
    }
    @Test void invalidEnumsRequiredFieldsAndOptionDuplicatesRejected() throws Exception {
        var result = preview(workbook(w -> {
            set(w, COURSE,2,"level","A9"); set(w, LESSONS,2,"status","ACTIVE");
            set(w, BLOCKS,2,"type","VIDEO"); set(w, VOCABULARY,2,"difficulty","1");
            set(w, UNITS,2,"title_vi",""); set(w, OPTIONS,3,"option_code","A");
        }));
        assertThat(result.errors()).extracting(CourseImportError::column).contains("level","status","type","difficulty","title_vi","option_code");
    }
    @Test void choiceRequiresOptionsAndObjectiveRequiresAnswer() throws Exception {
        var result = preview(workbook(w -> {
            var sheet = w.getSheet(OPTIONS.sheetName); sheet.removeRow(sheet.getRow(2)); sheet.removeRow(sheet.getRow(1));
            set(w, EXERCISES,2,"correct_answer","");
        }));
        assertThat(result.errors()).extracting(CourseImportError::column).contains("exercise_code","correct_answer");
    }
    @Test void formulaAndMalformedFilesAreRejected() throws Exception {
        assertThat(preview("not excel".getBytes()).success()).isFalse();
        assertThat(validator.validate(workbook(w -> {}),"bad.xls",ImportMode.CREATE_ONLY).success()).isFalse();
        assertThat(preview(workbook(w -> w.getSheet(COURSE.sheetName).getRow(1).getCell(6).setCellFormula("1+1"))).success()).isFalse();
    }
    @Test void createOnlyRejectsExistingCourseAndImportRevalidatesAfterPreview() throws Exception {
        var bytes = workbook(w -> {});
        assertThat(preview(bytes).success()).isTrue();
        create(bytes);
        assertThat(preview(bytes).success()).isFalse();
        assertThatThrownBy(() -> create(bytes)).isInstanceOf(CourseImportRejected.class);
        assertThat(count("courses")).isEqualTo(1);
    }
    @Test void updateUpsertsWithoutDuplicatesDeletionOrLosingProgress() throws Exception {
        create(workbook(w -> {}));
        long course = jdbc.queryForObject("select id from courses",Long.class);
        long lesson = jdbc.queryForObject("select id from lessons",Long.class);
        User user = new User(); user.setUsername("learner@test.local"); user.setEmail("learner@test.local"); user.setPasswordHash("unused"); users.saveAndFlush(user);
        jdbc.update("insert into enrollments(user_id,course_id,enrolled_at,status) values(?,?,CURRENT_TIMESTAMP,'ACTIVE')",user.getId(),course);
        long enrollment = jdbc.queryForObject("select id from enrollments",Long.class);
        jdbc.update("insert into lesson_progress(enrollment_id,lesson_id,status) values(?,?,'COMPLETED')",enrollment,lesson);
        var updated = workbook(w -> {
            set(w, COURSE,2,"title_vi","Tiếng Việt cập nhật");
            set(w, LESSONS,2,"sort_order","2");
            add(w, LESSONS,"A1-U1,A1-L2,New lesson,Bài mới,New,Mới,20,1,PUBLISHED");
            w.getSheet(BLOCKS.sheetName).removeRow(w.getSheet(BLOCKS.sheetName).getRow(1));
        });
        assertThat(importer.importCourse(updated,"course.xlsx",ImportMode.UPDATE_EXISTING).success()).isTrue();
        importer.importCourse(updated,"course.xlsx",ImportMode.UPDATE_EXISTING);
        assertThat(count("courses")).isEqualTo(1); assertThat(count("lessons")).isEqualTo(2);
        assertThat(count("lesson_blocks")).isEqualTo(1); assertThat(count("dialogue_lines")).isEqualTo(1);
        assertThat(count("course_vocabulary")).isEqualTo(1); assertThat(count("sentence_drills")).isEqualTo(1);
        assertThat(count("exercise_options")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select id from lessons where code='A1-L1'",Long.class)).isEqualTo(lesson);
        assertThat(jdbc.queryForObject("select status from lesson_progress",String.class)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForList("select code from lessons order by lesson_order",String.class)).containsExactly("A1-L2","A1-L1");
    }
    @Test void updateRejectsCodeHijackingAndInvalidRetainedOptions() throws Exception {
        create(workbook(w -> {}));
        var hijack = workbook(w -> { set(w, COURSE,2,"code","VI-B1"); set(w, UNITS,2,"course_code","VI-B1"); });
        assertThat(validator.validate(hijack,"course.xlsx",ImportMode.UPDATE_EXISTING).success()).isFalse();
        var badOptions = workbook(w -> {
            set(w, EXERCISES,2,"correct_answer","B");
            var sheet = w.getSheet(OPTIONS.sheetName); sheet.removeRow(sheet.getRow(1));
            set(w, OPTIONS,3,"is_correct","true");
        });
        assertThat(validator.validate(badOptions,"course.xlsx",ImportMode.UPDATE_EXISTING).errors())
            .anySatisfy(e -> assertThat(e.message()).contains("Retained options"));
    }
    @Test void fatalPersistenceFailureRollsBackEntireCreateAndUpdate() throws Exception {
        doAnswer(invocation -> { invocation.callRealMethod(); throw new DataIntegrityViolationException("Injected failure after all inserts"); })
            .when(persistence).save(any());
        assertThatThrownBy(() -> create(workbook(w -> {}))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("courses")).isZero(); assertThat(count("exercise_options")).isZero();
        reset(persistence);
        create(workbook(w -> {}));
        doAnswer(invocation -> { invocation.callRealMethod(); throw new DataIntegrityViolationException("Injected failure after updates"); })
            .when(persistence).save(any());
        var updated = workbook(w -> set(w, COURSE,2,"title_vi","Must roll back"));
        assertThatThrownBy(() -> importer.importCourse(updated,"course.xlsx",ImportMode.UPDATE_EXISTING)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select title_vi from courses",String.class)).isEqualTo("Tiếng Việt A1");
        assertThat(count("exercise_options")).isEqualTo(2);
    }
    @Test void updateRejectsSortCollisionWithOmittedLesson() throws Exception {
        create(workbook(w -> add(w, LESSONS,"A1-U1,A1-L2,Second,Hai,Description,Mô tả,20,2,PUBLISHED")));
        var changed = workbook(w -> set(w, LESSONS,2,"sort_order","2"));
        assertThat(validator.validate(changed,"course.xlsx",ImportMode.UPDATE_EXISTING).errors())
            .anySatisfy(e -> { assertThat(e.column()).isEqualTo("sort_order"); assertThat(e.message()).contains("retained record"); });
        assertThatThrownBy(() -> importer.importCourse(changed,"course.xlsx",ImportMode.UPDATE_EXISTING)).isInstanceOf(CourseImportRejected.class);
        assertThat(jdbc.queryForObject("select unit_sort_order from lessons where code='A1-L1'",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentCreateCannotLeaveTwoOrPartialCourses() throws Exception {
        byte[] bytes = workbook(w -> {});
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                start.await();
                try { create(bytes); return true; }
                catch (CourseImportRejected | org.springframework.dao.DataAccessException expected) { return false; }
            };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS),second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count("courses")).isEqualTo(1); assertThat(count("lessons")).isEqualTo(1);
        assertThat(count("exercise_options")).isEqualTo(2);
    }
    @Test void adminOnlyBothEndpointsAndCsrfStillRequired() throws Exception {
        byte[] bytes = workbook(w -> {});
        for (String url : List.of("/api/admin/courses/import/validate","/api/admin/courses/import")) {
            mvc.perform(multipart(url).file(file(bytes)).param("importMode","CREATE_ONLY").with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(multipart(url).file(file(bytes)).param("importMode","CREATE_ONLY").with(csrf()).with(user("learner").roles("LEARNER"))).andExpect(status().isForbidden());
            mvc.perform(multipart(url).file(file(bytes)).param("importMode","CREATE_ONLY").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/admin/courses/import/validate").file(file(bytes)).with(csrf()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(count("courses")).isZero();
        mvc.perform(multipart("/api/admin/courses/import").file(file(bytes)).param("importMode","CREATE_ONLY").with(csrf()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statistics.lessons").value(1));
        mvc.perform(multipart("/api/admin/courses/import").file(file(bytes)).param("importMode","CREATE_ONLY").with(csrf()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].column").value("code"));
    }
    @Test void actualLoginLoadsPersistedAdminRole() throws Exception {
        var admin = new User(); admin.setUsername("admin@test.local"); admin.setEmail("admin@test.local"); admin.setPasswordHash(passwords.encode("Test-password-123")); admin.setRoles(Set.of(roles.findByName("ADMIN").orElseThrow())); users.saveAndFlush(admin);
        var login = mvc.perform(post("/api/login").param("username",admin.getEmail()).param("password","Test-password-123").with(csrf()))
            .andExpect(status().isNoContent()).andReturn();
        mvc.perform(multipart("/api/admin/courses/import/validate").file(file(workbook(w -> {})))
            .session((MockHttpSession) login.getRequest().getSession(false)).with(csrf())).andExpect(status().isOk());
    }
    @Test void missingFileOrModeIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/admin/courses/import").with(csrf()).with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/courses/import").file(file(workbook(w -> {}))).with(csrf()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
    }
}
