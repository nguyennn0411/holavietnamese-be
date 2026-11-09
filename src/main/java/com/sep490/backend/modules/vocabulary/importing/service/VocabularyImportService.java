package com.sep490.backend.modules.vocabulary.importing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import com.sep490.backend.modules.vocabulary.importing.entity.VocabularyImportJob;
import com.sep490.backend.modules.vocabulary.importing.repository.VocabularyImportJobRepository;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VocabularyImportService {
    private final VocabularyImportHandler vocabularyHandler;
    private final VocabularyExcelReader reader;
    private final VocabularyImportJobRepository jobs;
    private final UserRepository users;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    /** Tra loai du lieu va cau truc cot Excel duoc ho tro. */
    public List<Type> types() {
        return List.of(vocabularyHandler.type());
    }

    /** Doc va kiem tra file, luu ban xem truoc truoc khi xac nhan import. */
    @Transactional
    public VocabularyImportResponse preview(
            String module, byte[] bytes, String filename, LearnerPrincipal actor) {
        return preview(module, new java.io.ByteArrayInputStream(bytes), filename, actor);
    }

    /** Doc va kiem tra file, luu ban xem truoc truoc khi xac nhan import. */
    @Transactional
    public VocabularyImportResponse preview(
            String module, java.io.InputStream input, String filename, LearnerPrincipal actor) {
        long owner = actorId(actor);
        var handler = handler(module);
        List<Row> rows;
        List<Error> inputErrors;
        {
            try {
                byte[] bytes = input.readNBytes(VocabularyExcelReader.MAX_BYTES + 1);
                var parsed = reader.read(bytes, filename, handler.type());
                rows = parsed.rows();
                inputErrors = parsed.errors();
            } catch (java.io.IOException failure) {
                throw new LearningException(
                        LearningException.Kind.INVALID, "Không đọc được file upload.");
            }
        }
        var errors = new ArrayList<>(inputErrors);
        errors.addAll(handler.validate(rows));
        Map<String, Object> summary = errors.isEmpty() ? handler.previewSummary(rows) : Map.of();
        var user = users.findById(owner).orElseThrow(() -> LearningException.notFound("User"));
        var job = new VocabularyImportJob();
        job.setId(UUID.randomUUID().toString());
        job.setModule(module);
        job.setFilename(safeFilename(filename));
        job.setActorId(owner);
        job.setActorName(
                user.getFullName() == null || user.getFullName().isBlank()
                        ? user.getUsername()
                        : user.getFullName());
        job.setCreatedAt(clock.instant());
        job.setTotalRows(rows.size());
        job.setSummary(encode(summary));
        job.setPayload(encode(rows));
        setErrors(job, errors);
        job.setStatus(errors.isEmpty() ? "READY" : "VALIDATION_FAILED");
        return response(jobs.saveAndFlush(job), true);
    }

    /** Kiem tra lai ban xem truoc va luu du lieu mot lan cho moi phien import. */
    public VocabularyImportResponse confirm(String id, LearnerPrincipal actor) {
        long owner = actorId(actor);
        // Khoa module truoc khi khoa phien nhap de giu cung thu tu khoa.
        String module =
                jobs.findById(id)
                        .orElseThrow(() -> LearningException.notFound("Import"))
                        .getModule();
        try {
            return new TransactionTemplate(transactions)
                    .execute(
                            tx -> {
                                jdbc.queryForObject(
                                        "SELECT module FROM content_import_locks WHERE module=? FOR"
                                                + " UPDATE",
                                        String.class,
                                        handler(module).lockModule());
                                var job =
                                        jobs.lockById(id)
                                                .orElseThrow(
                                                        () -> LearningException.notFound("Import"));
                                if (!job.getActorId().equals(owner))
                                    throw new LearningException(
                                            LearningException.Kind.FORBIDDEN,
                                            "Chỉ người đã preview mới được xác nhận lần import"
                                                    + " này.");
                                if (completedSuccessfully(job.getStatus()))
                                    return response(job, true);
                                if (!"READY".equals(job.getStatus()))
                                    throw new LearningException(
                                            LearningException.Kind.CONFLICT,
                                            "Hãy sửa file và tạo preview mới trước khi xác nhận.");
                                var handler = handler(module);
                                var rows = rows(job);
                                var errors = handler.validate(rows);
                                job.setPayload(encode(rows));
                                if (!errors.isEmpty()) {
                                    setErrors(job, errors);
                                    job.setStatus("VALIDATION_FAILED");
                                    job.setCompletedAt(clock.instant());
                                    return response(jobs.saveAndFlush(job), true);
                                }
                                VocabularyImportHandler.WriteResult result;
                                try {
                                    result = handler.write(rows);
                                    em.flush();
                                } catch (RuntimeException failure) {
                                    throw new ImportWriteFailed(failure);
                                }
                                job.setImportedRows(result.importedRows());
                                job.setSummary(encode(result.summary()));
                                int skipped =
                                        summarySize(result.summary(), "duplicateWords")
                                                + summarySize(
                                                        result.summary(),
                                                        "duplicateRegionalVariants");
                                job.setStatus(
                                        result.importedRows() == 0
                                                ? "SKIPPED"
                                                : skipped > 0 ? "IMPORTED_WITH_SKIPS" : "IMPORTED");
                                job.setCompletedAt(clock.instant());
                                return response(jobs.saveAndFlush(job), true);
                            });
        } catch (ImportWriteFailed failure) {
            // Ghi trang thai loi trong transaction rieng sau khi rollback du lieu nhap.
            return new TransactionTemplate(transactions)
                    .execute(
                            tx -> {
                                var job =
                                        jobs.lockById(id)
                                                .orElseThrow(
                                                        () -> LearningException.notFound("Import"));
                                if (completedSuccessfully(job.getStatus()))
                                    return response(job, true);
                                job.setStatus("FAILED");
                                job.setImportedRows(0);
                                job.setCompletedAt(clock.instant());
                                setErrors(
                                        job,
                                        List.of(
                                                new Error(
                                                        handler(module).type().sheet(),
                                                        0,
                                                        "database",
                                                        "Không ghi được dữ liệu. Toàn bộ lần import"
                                                            + " đã được hoàn tác; hãy tạo preview"
                                                            + " mới.")));
                                return response(jobs.saveAndFlush(job), true);
                            });
        }
    }

    /** Kiem tra va luu cac dong nguoi dung sua trong ban xem truoc. */
    @Transactional
    public VocabularyImportResponse updatePreview(
            String id, List<Row> requestedRows, LearnerPrincipal actor) {
        long owner = actorId(actor);
        var job = jobs.lockById(id).orElseThrow(() -> LearningException.notFound("Import"));
        if (!job.getActorId().equals(owner))
            throw new LearningException(
                    LearningException.Kind.FORBIDDEN,
                    "Chỉ người đã preview mới được chỉnh sửa lần import này.");
        if (!Set.of("READY", "VALIDATION_FAILED").contains(job.getStatus()))
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Lần import đã hoàn tất, không thể chỉnh sửa dữ liệu xem trước.");
        var handler = handler(job.getModule());
        var rows = sanitizeRows(requestedRows, handler.type());
        var errors = handler.validate(rows);
        var summary = errors.isEmpty() ? handler.previewSummary(rows) : Map.<String, Object>of();
        job.setPayload(encode(rows));
        job.setSummary(encode(summary));
        job.setTotalRows(rows.size());
        job.setImportedRows(0);
        job.setCompletedAt(null);
        setErrors(job, errors);
        job.setStatus(errors.isEmpty() ? "READY" : "VALIDATION_FAILED");
        return response(jobs.saveAndFlush(job), true);
    }

    /** Tra lich su import theo phan trang. */
    @Transactional(readOnly = true)
    public Page<VocabularyImportResponse> history(int page, int size) {
        return jobs.findAll(
                        PageRequest.of(
                                Math.max(0, page),
                                Math.max(1, Math.min(size, 100)),
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(job -> response(job, false));
    }

    /** Lay chi tiet phien import va ket qua xu ly tung dong. */
    @Transactional(readOnly = true)
    public VocabularyImportResponse detail(String id) {
        return response(
                jobs.findById(id).orElseThrow(() -> LearningException.notFound("Import")), true);
    }

    /** Lay cau truc cot va quy tac cua loai import duoc ho tro. */
    public Type type(String module) {
        return handler(module).type();
    }

    /** Kiem tra loai import va lay bo xu ly tu vung tuong ung. */
    private VocabularyImportHandler handler(String type) {
        if (!vocabularyHandler.type().id().equals(type)) {
            throw new LearningException(
                    LearningException.Kind.INVALID, "Loại import chưa được hỗ trợ.");
        }
        return vocabularyHandler;
    }

    /** Tong hop so dong loi va so dong hop le cua phien import. */
    private void setErrors(VocabularyImportJob job, List<Error> errors) {
        job.setDiagnostics(encode(errors));
        int firstDataRow = 2;
        long invalidRows =
                errors.stream()
                        .filter(e -> e.row() >= firstDataRow)
                        .map(Error::row)
                        .distinct()
                        .count();
        boolean fileError = errors.stream().anyMatch(e -> e.row() < firstDataRow);
        job.setErrorRows(fileError ? job.getTotalRows() : (int) invalidRows);
        job.setValidRows(job.getTotalRows() - job.getErrorRows());
    }

    /** Chuyen du lieu da xu ly sang response tra ve nguoi dung. */
    private VocabularyImportResponse response(VocabularyImportJob job, boolean includeData) {
        var summary = summary(job);
        String status = resolvedStatus(job.getStatus(), job.getImportedRows(), summary);
        boolean done = completedSuccessfully(status);
        return new VocabularyImportResponse(
                job.getId(),
                job.getModule(),
                job.getFilename(),
                job.getActorId(),
                job.getActorName(),
                status,
                job.getTotalRows(),
                job.getValidRows(),
                job.getErrorRows(),
                job.getImportedRows(),
                "FAILED".equals(job.getStatus()) ? job.getTotalRows() : job.getErrorRows(),
                "READY".equals(job.getStatus())
                        && vocabularyHandler.type().id().equals(job.getModule()),
                done,
                job.getCreatedAt(),
                job.getCompletedAt(),
                includeData ? rows(job) : List.of(),
                includeData
                        ? decode(job.getDiagnostics(), new TypeReference<List<Error>>() {})
                        : List.of(),
                summary);
    }

    /** Tong hop ket qua import de hien thi va luu lich su. */
    private Map<String, Object> summary(VocabularyImportJob job) {
        var data = new LinkedHashMap<String, Object>();
        if (job.getSummary() != null)
            data.putAll(decode(job.getSummary(), new TypeReference<Map<String, Object>>() {}));
        return data;
    }

    /** Doc lai cac dong da luu trong ban xem truoc. */
    private List<Row> rows(VocabularyImportJob job) {
        return decode(job.getPayload(), new TypeReference<List<Row>>() {});
    }

    /** Kiem tra cac cot va gia tri cua ban xem truoc do nguoi dung gui len. */
    private List<Row> sanitizeRows(List<Row> requestedRows, Type type) {
        if (requestedRows == null || requestedRows.isEmpty() || requestedRows.size() > 20000)
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Bản xem trước phải có từ 1 đến 20.000 dòng dữ liệu.");
        var result = new ArrayList<Row>(requestedRows.size());
        var numbers = new HashSet<Integer>();
        for (var row : requestedRows) {
            if (row == null
                    || row.rowNumber() < 2
                    || row.rowNumber() > 20001
                    || !numbers.add(row.rowNumber()))
                throw new LearningException(
                        LearningException.Kind.INVALID,
                        "Số dòng nguồn trong bản xem trước không hợp lệ hoặc bị trùng.");
            var values = new LinkedHashMap<String, String>();
            for (String column : type.columns()) {
                String value =
                        row.values() == null
                                ? ""
                                : Objects.toString(row.values().get(column), "").strip();
                if (value.length() > 10000)
                    throw new LearningException(
                            LearningException.Kind.INVALID,
                            "Mỗi ô trong bản xem trước tối đa 10.000 ký tự.");
                values.put(column, value);
            }
            result.add(new Row(row.rowNumber(), values));
        }
        return List.copyOf(result);
    }

    /** Dem so muc trong mot nhom ket qua import. */
    private static int summarySize(Map<String, Object> summary, String key) {
        Object value = summary.get(key);
        return value instanceof Collection<?> items ? items.size() : 0;
    }

    /** Xac dinh phien import da hoan tat thanh cong. */
    private static boolean completedSuccessfully(String status) {
        return Set.of("IMPORTED", "IMPORTED_WITH_SKIPS", "SKIPPED").contains(status);
    }

    /** Xac dinh trang thai tra ve tu ket qua luu va thong tin trung lap. */
    private static String resolvedStatus(
            String storedStatus, int importedRows, Map<String, Object> summary) {
        if (!"IMPORTED".equals(storedStatus)) return storedStatus;
        int skipped =
                summarySize(summary, "duplicateWords")
                        + summarySize(summary, "duplicateRegionalVariants");
        if (skipped == 0) return storedStatus;
        return importedRows == 0 ? "SKIPPED" : "IMPORTED_WITH_SKIPS";
    }

    /** Chuyen du lieu sang JSON de luu cung phien import. */
    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize import data", e);
        }
    }

    /** Doc du lieu JSON da luu theo kieu du lieu can su dung. */
    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read stored import data", e);
        }
    }

    /** Lay ID nguoi thuc hien va kiem tra thong tin dang nhap. */
    private static long actorId(LearnerPrincipal actor) {
        if (actor == null || actor.id() == null)
            throw new LearningException(
                    LearningException.Kind.FORBIDDEN, "Authenticated admin required.");
        return actor.id();
    }

    /** Rut gon ten file upload theo gioi han duoc ho tro. */
    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) return "upload.xlsx";
        String clean = name.replace('\\', '/').replaceAll("[\\p{Cntrl}]", "");
        clean = clean.substring(clean.lastIndexOf('/') + 1);
        return clean.substring(0, Math.min(clean.length(), 255));
    }

    private static class ImportWriteFailed extends RuntimeException {
        ImportWriteFailed(Throwable cause) {
            super(cause);
        }
    }
}
