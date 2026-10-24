package com.sep490.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.response.ContentImportResponse;
import com.sep490.backend.dto.response.ContentImportResponse.*;
import com.sep490.backend.dto.response.ContentImportResponse.Error;
import com.sep490.backend.entity.ContentImportJobJpaEntity;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.repository.UserRepository;
import com.sep490.backend.repository.jpa.ContentImportJobJpaRepository;
import com.sep490.backend.service.importing.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service @RequiredArgsConstructor
public class ContentImportService {
    private final List<ContentImportHandler> handlers;
    private final ContentExcelReader reader;
    private final ContentImportJobJpaRepository jobs;
    private final UserRepository users;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public List<Type> types() { return handlers.stream().map(ContentImportHandler::type).sorted(java.util.Comparator.comparing(t -> !t.id().equals("VOCABULARY"))).toList(); }
    @Transactional public ContentImportResponse preview(String module, byte[] bytes, String filename, LearnerPrincipal actor) {
        return preview(module,new java.io.ByteArrayInputStream(bytes),filename,actor);
    }
    @Transactional public ContentImportResponse preview(String module, java.io.InputStream input, String filename, LearnerPrincipal actor) {
        long owner = actorId(actor); var handler = handler(module);
        List<Row> rows; List<Error> inputErrors; Map<String,Object> summary=Map.of();
        {
            try {
                byte[] bytes=input.readNBytes((int)ExcelCourseParser.MAX_BYTES+1);
                var parsed=reader.read(bytes,filename,handler.type()); rows=parsed.rows(); inputErrors=parsed.errors();
            } catch(java.io.IOException failure) { throw new LearningException(LearningException.Kind.INVALID,"Không đọc được file upload."); }
        }
        var errors = new ArrayList<>(inputErrors);
        errors.addAll(handler.validate(rows));
        var user = users.findById(owner).orElseThrow(() -> LearningException.notFound("User"));
        var job = new ContentImportJobJpaEntity();
        job.setId(UUID.randomUUID().toString()); job.setModule(module); job.setFilename(safeFilename(filename));
        job.setActorId(owner); job.setActorName(user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName());
        job.setCreatedAt(clock.instant()); job.setTotalRows(rows.size());
        job.setSummary(encode(summary)); job.setPayload(encode(rows)); setErrors(job,errors);
        job.setStatus(errors.isEmpty() ? "READY" : "VALIDATION_FAILED");
        return response(jobs.saveAndFlush(job),true);
    }
    public ContentImportResponse confirm(String id, LearnerPrincipal actor) {
        long owner = actorId(actor);
        // Read module first, then lock the handler before the job in a consistent order.
        String module = jobs.findById(id).orElseThrow(() -> LearningException.notFound("Import")).getModule();
        try {
            return new TransactionTemplate(transactions).execute(tx -> {
                jdbc.queryForObject("SELECT module FROM content_import_locks WHERE module=? FOR UPDATE",String.class,handler(module).lockModule());
                var job = jobs.lockById(id).orElseThrow(() -> LearningException.notFound("Import"));
                if (!job.getActorId().equals(owner)) throw new LearningException(LearningException.Kind.FORBIDDEN,"Chỉ người đã preview mới được xác nhận lần import này.");
                if ("IMPORTED".equals(job.getStatus())) return response(job,true);
                if (!"READY".equals(job.getStatus())) throw new LearningException(LearningException.Kind.CONFLICT,"Hãy sửa file và tạo preview mới trước khi xác nhận.");
                var handler = handler(module); var rows = rows(job);
                var errors = handler.validate(rows);
                job.setPayload(encode(rows));
                if (!errors.isEmpty()) {
                    setErrors(job,errors); job.setStatus("VALIDATION_FAILED"); job.setCompletedAt(clock.instant());
                    return response(jobs.saveAndFlush(job),true);
                }
                try { handler.write(rows); em.flush(); }
                catch (RuntimeException failure) { throw new ImportWriteFailed(failure); }
                job.setImportedRows(handler.importCount(rows)); job.setStatus("IMPORTED"); job.setCompletedAt(clock.instant());
                return response(jobs.saveAndFlush(job),true);
            });
        } catch (ImportWriteFailed failure) {
            // Business writes rolled back. Record a failure in a separate transaction, without leaking SQL.
            return new TransactionTemplate(transactions).execute(tx -> {
                var job = jobs.lockById(id).orElseThrow(() -> LearningException.notFound("Import"));
                if ("IMPORTED".equals(job.getStatus())) return response(job,true);
                job.setStatus("FAILED"); job.setImportedRows(0); job.setCompletedAt(clock.instant());
                setErrors(job,List.of(new Error(handler(module).type().sheet(),0,"database","Không ghi được dữ liệu. Toàn bộ lần import đã được hoàn tác; hãy tạo preview mới.")));
                return response(jobs.saveAndFlush(job),true);
            });
        }
    }
    @Transactional(readOnly = true) public Page<ContentImportResponse> history(int page, int size) {
        return jobs.findAll(PageRequest.of(Math.max(0,page),Math.max(1,Math.min(size,100)),Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))))
                .map(job -> response(job,false));
    }
    @Transactional(readOnly = true) public ContentImportResponse detail(String id) {
        return response(jobs.findById(id).orElseThrow(() -> LearningException.notFound("Import")),true);
    }
    public Type type(String module) { return handler(module).type(); }
    private ContentImportHandler handler(String type) {
        return handlers.stream().filter(h -> h.type().id().equals(type)).findFirst().orElseThrow(() -> new LearningException(LearningException.Kind.INVALID,"Loại import chưa được hỗ trợ."));
    }
    private void setErrors(ContentImportJobJpaEntity job,List<Error> errors) {
        job.setDiagnostics(encode(errors));
        int firstDataRow=2;
        long invalidRows = errors.stream().filter(e -> e.row() >= firstDataRow).map(Error::row).distinct().count();
        boolean fileError = errors.stream().anyMatch(e -> e.row() < firstDataRow);
        job.setErrorRows(fileError ? job.getTotalRows() : (int)invalidRows);
        job.setValidRows(job.getTotalRows()-job.getErrorRows());
    }
    private ContentImportResponse response(ContentImportJobJpaEntity job,boolean includeData) {
        boolean done = "IMPORTED".equals(job.getStatus());
        return new ContentImportResponse(job.getId(),job.getModule(),job.getFilename(),job.getActorId(),job.getActorName(),job.getStatus(),
                job.getTotalRows(),job.getValidRows(),job.getErrorRows(),job.getImportedRows(),
                "FAILED".equals(job.getStatus()) ? job.getTotalRows() : job.getErrorRows(),
                "READY".equals(job.getStatus()) && handlers.stream().anyMatch(h -> h.type().id().equals(job.getModule())),done,job.getCreatedAt(),job.getCompletedAt(),
                includeData ? rows(job) : List.of(),includeData ? decode(job.getDiagnostics(),new TypeReference<List<Error>>() {}) : List.of(),summary(job));
    }
    private Map<String,Object> summary(ContentImportJobJpaEntity job) {
        var data=new LinkedHashMap<String,Object>();
        if(job.getSummary()!=null) data.putAll(decode(job.getSummary(),new TypeReference<Map<String,Object>>() {}));
        return data;
    }
    private List<Row> rows(ContentImportJobJpaEntity job) { return decode(job.getPayload(),new TypeReference<List<Row>>() {}); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot serialize import data",e); } }
    private <T> T decode(String value,TypeReference<T> type) { try { return json.readValue(value,type); } catch (Exception e) { throw new IllegalStateException("Cannot read stored import data",e); } }
    private static long actorId(LearnerPrincipal actor) {
        if (actor == null || actor.id() == null) throw new LearningException(LearningException.Kind.FORBIDDEN,"Authenticated admin required.");
        return actor.id();
    }
    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) return "upload.xlsx";
        String clean = name.replace('\\','/').replaceAll("[\\p{Cntrl}]","");
        clean = clean.substring(clean.lastIndexOf('/')+1);
        return clean.substring(0,Math.min(clean.length(),255));
    }
    private static class ImportWriteFailed extends RuntimeException { ImportWriteFailed(Throwable cause) { super(cause); } }
}
