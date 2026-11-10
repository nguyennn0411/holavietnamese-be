package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Row;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Type;
import com.sep490.backend.modules.vocabulary.importing.entity.VocabularyImportJob;
import com.sep490.backend.modules.vocabulary.importing.repository.VocabularyImportJobRepository;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyExcelReader;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyImportHandler;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyImportService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/** Unit tests for import ownership, state checks, paging bounds and preview sanitization. */
class VocabularyImportServiceTests {
    private VocabularyImportHandler handler;
    private VocabularyImportJobRepository jobs;
    private VocabularyExcelReader reader;
    private UserRepository users;
    private PlatformTransactionManager transactions;
    private JdbcTemplate jdbc;
    private EntityManager entityManager;
    private VocabularyImportService service;
    private final Type type =
            new Type(
                    "VOCABULARY", "Vocabulary", "VOCABULARY",
                    List.of("word", "part_of_speech"), List.of("word", "part_of_speech"));
    private final LearnerPrincipal owner = new LearnerPrincipal(7L, "owner@test.local", "", true, "CONTENT");

    @BeforeEach
    void setUp() {
        handler = mock(VocabularyImportHandler.class);
        jobs = mock(VocabularyImportJobRepository.class);
        reader = mock(VocabularyExcelReader.class);
        users = mock(UserRepository.class);
        transactions = mock(PlatformTransactionManager.class);
        jdbc = mock(JdbcTemplate.class);
        entityManager = mock(EntityManager.class);
        when(handler.type()).thenReturn(type);
        // TransactionTemplate only needs a status object; persistence itself is mocked here.
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jobs.saveAndFlush(any(VocabularyImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service =
                new VocabularyImportService(
                        handler,
                        reader,
                        jobs,
                        users,
                        new ObjectMapper(),
                        transactions,
                        jdbc,
                        entityManager,
                        Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void typesAndTypeRejectUnsupportedModules() {
        assertThat(service.types()).containsExactly(type);
        assertThat(service.type("VOCABULARY")).isEqualTo(type);
        assertKind(() -> service.type("COURSE"), LearningException.Kind.INVALID);
    }

    @Test
    void previewRequiresAuthenticatedActorBeforeReadingInput() {
        assertKind(
                () -> service.preview("VOCABULARY", new byte[0], "file.xlsx", null),
                LearningException.Kind.FORBIDDEN);
    }

    @Test
    void previewStoresReadyJobWithSanitizedFilenameAndActorFallback() {
        var parsedRows = List.of(row(2));
        when(reader.read(any(byte[].class), any(), any()))
                .thenReturn(new VocabularyExcelReader.WorkbookData(parsedRows, List.of()));
        when(handler.validate(parsedRows)).thenReturn(List.of());
        when(handler.previewSummary(parsedRows)).thenReturn(Map.of("newWords", 1));
        var user = new User();
        user.setUsername("owner");
        user.setFullName("  "); // blank display name falls back to username
        when(users.findById(7L)).thenReturn(Optional.of(user));

        var response = service.preview("VOCABULARY", new byte[] {1}, "C:\\temp\\words.xlsx", owner);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.filename()).isEqualTo("words.xlsx");
        assertThat(response.actorName()).isEqualTo("owner");
        assertThat(response.totalRows()).isOne();
        assertThat(response.validRows()).isOne();
        assertThat(response.canConfirm()).isTrue();
        assertThat(response.summary()).containsEntry("newWords", 1);
    }

    @Test
    void previewCombinesReaderAndDomainErrorsAndHandlesReadFailure() throws Exception {
        var parsedRows = List.of(row(2));
        var fileError = new Error("VOCABULARY", 0, "file", "bad workbook");
        var rowError = new Error("VOCABULARY", 2, "word", "required");
        when(reader.read(any(byte[].class), any(), any()))
                .thenReturn(new VocabularyExcelReader.WorkbookData(parsedRows, List.of(fileError)));
        when(handler.validate(parsedRows)).thenReturn(List.of(rowError));
        var user = new User();
        user.setFullName("Content Admin");
        when(users.findById(7L)).thenReturn(Optional.of(user));

        var response = service.preview("VOCABULARY", new byte[] {1}, null, owner);

        assertThat(response.status()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.filename()).isEqualTo("upload.xlsx");
        assertThat(response.errorRows()).isOne(); // file errors invalidate every parsed row
        assertThat(response.errors()).hasSize(2);
        assertThat(response.summary()).isEmpty();

        InputStream broken = mock(InputStream.class);
        when(broken.readNBytes(anyInt())).thenThrow(new IOException("stream closed"));
        assertKind(
                () -> service.preview("VOCABULARY", broken, "words.xlsx", owner),
                LearningException.Kind.INVALID);
    }

    @Test
    void historyClampsPageAndSizeAndOmitsStoredRowDetails() {
        var job = job("job-1", 7L, "READY");
        when(jobs.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(job)));

        var page = service.history(-2, 500);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(page.getContent()).singleElement()
                .satisfies(response -> {
                    assertThat(response.rows()).isEmpty();
                    assertThat(response.errors()).isEmpty();
                });
    }

    @Test
    void detailReturnsStoredDataOrNotFound() {
        var job = job("job-1", 7L, "READY");
        when(jobs.findById("job-1")).thenReturn(Optional.of(job));
        when(jobs.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.detail("job-1").id()).isEqualTo("job-1");
        assertKind(() -> service.detail("missing"), LearningException.Kind.NOT_FOUND);
    }

    @Test
    void updatePreviewEnforcesOwnerEditableStatusAndValidRowNumbers() {
        var foreign = job("foreign", 8L, "READY");
        when(jobs.lockById("foreign")).thenReturn(Optional.of(foreign));
        assertKind(
                () -> service.updatePreview("foreign", List.of(row(2)), owner),
                LearningException.Kind.FORBIDDEN);

        var completed = job("completed", 7L, "IMPORTED");
        when(jobs.lockById("completed")).thenReturn(Optional.of(completed));
        assertKind(
                () -> service.updatePreview("completed", List.of(row(2)), owner),
                LearningException.Kind.CONFLICT);

        var editable = job("editable", 7L, "READY");
        when(jobs.lockById("editable")).thenReturn(Optional.of(editable));
        assertKind(
                () -> service.updatePreview("editable", List.of(row(1)), owner),
                LearningException.Kind.INVALID);
        assertKind(
                () -> service.updatePreview("editable", List.of(row(2), row(2)), owner),
                LearningException.Kind.INVALID);
        assertKind(
                () -> service.updatePreview("editable", null, owner),
                LearningException.Kind.INVALID);
    }

    @Test
    void updatePreviewSanitizesValuesAndRecomputesStatus() {
        var editable = job("editable", 7L, "VALIDATION_FAILED");
        when(jobs.lockById("editable")).thenReturn(Optional.of(editable));
        var requested = List.of(new Row(2, Map.of("word", "  xin chào  ")));
        when(handler.validate(any())).thenReturn(List.of());
        when(handler.previewSummary(any())).thenReturn(Map.of("newWords", 1));

        var response = service.updatePreview("editable", requested, owner);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.rows()).singleElement().satisfies(row -> {
            assertThat(row.values().get("word")).isEqualTo("xin chào");
            assertThat(row.values().get("part_of_speech")).isEmpty();
        });
        assertThat(response.validRows()).isOne();
        assertThat(editable.getCompletedAt()).isNull();
    }

    @Test
    void confirmRejectsForeignAndInvalidStateButReturnsCompletedJobIdempotently() {
        var foreign = job("foreign", 8L, "READY");
        when(jobs.findById("foreign")).thenReturn(Optional.of(foreign));
        when(jobs.lockById("foreign")).thenReturn(Optional.of(foreign));
        assertKind(() -> service.confirm("foreign", owner), LearningException.Kind.FORBIDDEN);

        var invalid = job("invalid", 7L, "VALIDATION_FAILED");
        when(jobs.findById("invalid")).thenReturn(Optional.of(invalid));
        when(jobs.lockById("invalid")).thenReturn(Optional.of(invalid));
        assertKind(() -> service.confirm("invalid", owner), LearningException.Kind.CONFLICT);

        var completed = job("completed", 7L, "IMPORTED");
        completed.setImportedRows(1);
        when(jobs.findById("completed")).thenReturn(Optional.of(completed));
        when(jobs.lockById("completed")).thenReturn(Optional.of(completed));
        assertThat(service.confirm("completed", owner).status()).isEqualTo("IMPORTED");
    }

    @Test
    void confirmRevalidatesThenImportsAndResolvesSkippedDuplicates() {
        var ready = job("ready", 7L, "READY");
        ready.setPayload(new ObjectMapper().valueToTree(List.of(row(2))).toString());
        when(jobs.findById("ready")).thenReturn(Optional.of(ready));
        when(jobs.lockById("ready")).thenReturn(Optional.of(ready));
        when(handler.validate(any())).thenReturn(List.of());
        when(handler.write(any()))
                .thenReturn(
                        new VocabularyImportHandler.WriteResult(
                                1, Map.of("duplicateWords", List.of("xin"))));

        var response = service.confirm("ready", owner);

        assertThat(response.status()).isEqualTo("IMPORTED_WITH_SKIPS");
        assertThat(response.importedRows()).isOne();
        assertThat(response.success()).isTrue();
        verify(entityManager).flush();
    }

    @Test
    void confirmRollsBackWriteAndPersistsFailedStatus() {
        var ready = job("failed", 7L, "READY");
        ready.setPayload(new ObjectMapper().valueToTree(List.of(row(2))).toString());
        when(jobs.findById("failed")).thenReturn(Optional.of(ready));
        // First lock belongs to the import transaction, second to the failure transaction.
        when(jobs.lockById("failed")).thenReturn(Optional.of(ready), Optional.of(ready));
        when(handler.validate(any())).thenReturn(List.of());
        when(handler.write(any())).thenThrow(new IllegalStateException("database unavailable"));

        var response = service.confirm("failed", owner);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.importedRows()).isZero();
        assertThat(response.errors()).singleElement()
                .satisfies(error -> assertThat(error.column()).isEqualTo("database"));
    }

    private static Row row(int number) {
        return new Row(number, Map.of("word", "xin", "part_of_speech", "noun"));
    }

    private static VocabularyImportJob job(String id, long actor, String status) {
        var job = new VocabularyImportJob();
        job.setId(id);
        job.setModule("VOCABULARY");
        job.setFilename("file.xlsx");
        job.setActorId(actor);
        job.setActorName("Owner");
        job.setStatus(status);
        job.setPayload("[]");
        job.setDiagnostics("[]");
        job.setSummary("{}");
        job.setCreatedAt(Instant.parse("2026-11-10T00:00:00Z"));
        return job;
    }

    private static void assertKind(Runnable action, LearningException.Kind expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        LearningException.class,
                        error -> assertThat(error.kind()).isEqualTo(expected));
    }
}
