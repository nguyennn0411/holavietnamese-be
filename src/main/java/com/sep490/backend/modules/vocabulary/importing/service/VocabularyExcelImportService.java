package com.sep490.backend.modules.vocabulary.importing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import com.sep490.backend.modules.vocabulary.importing.entity.VocabularyImportJobJpaEntity;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.vocabulary.importing.repository.jpa.VocabularyImportJobJpaRepository;
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
public class VocabularyExcelImportService {
    private final List<VocabularyImportHandlerContract> handlers;
    private final VocabularyExcelReader reader;
    private final VocabularyImportJobJpaRepository jobs;
    private final UserRepository users;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;

    public List<Type> types() { return handlers.stream().map(VocabularyImportHandlerContract::type).sorted(java.util.Comparator.comparing(t -> !t.id().equals("VOCABULARY"))).toList(); }
    @Transactional public VocabularyImportResponse preview(String module, byte[] bytes, String filename, LearnerPrincipal actor) {
        return preview(module,new java.io.ByteArrayInputStream(bytes),filename,actor);
    }
    @Transactional public VocabularyImportResponse preview(String module, java.io.InputStream input, String filename, LearnerPrincipal actor) {
        long owner = actorId(actor); var handler = handler(module);
        List<Row> rows; List<Error> inputErrors;
        {
            try {
                byte[] bytes=input.readNBytes(VocabularyExcelReader.MAX_BYTES+1);
                var parsed=reader.read(bytes,filename,handler.type()); rows=parsed.rows(); inputErrors=parsed.errors();
            } catch(java.io.IOException failure) { throw new LearningException(LearningException.Kind.INVALID,"Không đọc được file upload."); }
        }
        var errors = new ArrayList<>(inputErrors);
        errors.addAll(handler.validate(rows));
        Map<String,Object> summary=errors.isEmpty()?handler.previewSummary(rows):Map.of();
        var user = users.findById(owner).orElseThrow(() -> LearningException.notFound("User"));
        var job = new VocabularyImportJobJpaEntity();
        job.setId(UUID.randomUUID().toString()); job.setModule(module); job.setFilename(safeFilename(filename));
        job.setActorId(owner); job.setActorName(user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName());
        job.setCreatedAt(clock.instant()); job.setTotalRows(rows.size());
        job.setSummary(encode(summary)); job.setPayload(encode(rows)); setErrors(job,errors);
        job.setStatus(errors.isEmpty() ? "READY" : "VALIDATION_FAILED");
        return response(jobs.saveAndFlush(job),true);
    }
    public VocabularyImportResponse confirm(String id, LearnerPrincipal actor) {
        long owner = actorId(actor);
        // Read module first, then lock the handler before the job in a consistent order.
        String module = jobs.findById(id).orElseThrow(() -> LearningException.notFound("Import")).getModule();
        try {
            return new TransactionTemplate(transactions).execute(tx -> {
                jdbc.queryForObject("SELECT module FROM content_import_locks WHERE module=? FOR UPDATE",String.class,handler(module).lockModule());
                var job = jobs.lockById(id).orElseThrow(() -> LearningException.notFound("Import"));
                if (!job.getActorId().equals(owner)) throw new LearningException(LearningException.Kind.FORBIDDEN,"Chỉ người đã preview mới được xác nhận lần import này.");
                if (completedSuccessfully(job.getStatus())) return response(job,true);
                if (!"READY".equals(job.getStatus())) throw new LearningException(LearningException.Kind.CONFLICT,"Hãy sửa file và tạo preview mới trước khi xác nhận.");
                var handler = handler(module); var rows = rows(job);
                var errors = handler.validate(rows);
                job.setPayload(encode(rows));
                if (!errors.isEmpty()) {
                    setErrors(job,errors); job.setStatus("VALIDATION_FAILED"); job.setCompletedAt(clock.instant());
                    return response(jobs.saveAndFlush(job),true);
                }
                VocabularyImportHandlerContract.WriteResult result;
                try { result=handler.write(rows); em.flush(); }
                catch (RuntimeException failure) { throw new ImportWriteFailed(failure); }
                job.setImportedRows(result.importedRows()); job.setSummary(encode(result.summary()));
                int skipped = summarySize(result.summary(),"duplicateWords") + summarySize(result.summary(),"duplicateRegionalVariants");
                job.setStatus(result.importedRows() == 0 ? "SKIPPED" : skipped > 0 ? "IMPORTED_WITH_SKIPS" : "IMPORTED");
                job.setCompletedAt(clock.instant());
                return response(jobs.saveAndFlush(job),true);
            });
        } catch (ImportWriteFailed failure) {
            // Business writes rolled back. Record a failure in a separate transaction, without leaking SQL.
            return new TransactionTemplate(transactions).execute(tx -> {
                var job = jobs.lockById(id).orElseThrow(() -> LearningException.notFound("Import"));
                if (completedSuccessfully(job.getStatus())) return response(job,true);
                job.setStatus("FAILED"); job.setImportedRows(0); job.setCompletedAt(clock.instant());
                setErrors(job,List.of(new Error(handler(module).type().sheet(),0,"database","Không ghi được dữ liệu. Toàn bộ lần import đã được hoàn tác; hãy tạo preview mới.")));
                return response(jobs.saveAndFlush(job),true);
            });
        }
    }
    @Transactional public VocabularyImportResponse updatePreview(String id,List<Row> requestedRows,LearnerPrincipal actor) {
        long owner=actorId(actor);
        var job=jobs.lockById(id).orElseThrow(() -> LearningException.notFound("Import"));
        if(!job.getActorId().equals(owner))
            throw new LearningException(LearningException.Kind.FORBIDDEN,"Chỉ người đã preview mới được chỉnh sửa lần import này.");
        if(!Set.of("READY","VALIDATION_FAILED").contains(job.getStatus()))
            throw new LearningException(LearningException.Kind.CONFLICT,"Lần import đã hoàn tất, không thể chỉnh sửa dữ liệu xem trước.");
        var handler=handler(job.getModule());
        var rows=sanitizeRows(requestedRows,handler.type());
        var errors=handler.validate(rows);
        var summary=errors.isEmpty()?handler.previewSummary(rows):Map.<String,Object>of();
        job.setPayload(encode(rows)); job.setSummary(encode(summary)); job.setTotalRows(rows.size());
        job.setImportedRows(0); job.setCompletedAt(null); setErrors(job,errors);
        job.setStatus(errors.isEmpty()?"READY":"VALIDATION_FAILED");
        return response(jobs.saveAndFlush(job),true);
    }
    @Transactional(readOnly = true) public Page<VocabularyImportResponse> history(int page, int size) {
        return jobs.findAll(PageRequest.of(Math.max(0,page),Math.max(1,Math.min(size,100)),Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))))
                .map(job -> response(job,false));
    }
    @Transactional(readOnly = true) public VocabularyImportResponse detail(String id) {
        return response(jobs.findById(id).orElseThrow(() -> LearningException.notFound("Import")),true);
    }
    public Type type(String module) { return handler(module).type(); }
    private VocabularyImportHandlerContract handler(String type) {
        return handlers.stream().filter(h -> h.type().id().equals(type)).findFirst().orElseThrow(() -> new LearningException(LearningException.Kind.INVALID,"Loại import chưa được hỗ trợ."));
    }
    private void setErrors(VocabularyImportJobJpaEntity job,List<Error> errors) {
        job.setDiagnostics(encode(errors));
        int firstDataRow=2;
        long invalidRows = errors.stream().filter(e -> e.row() >= firstDataRow).map(Error::row).distinct().count();
        boolean fileError = errors.stream().anyMatch(e -> e.row() < firstDataRow);
        job.setErrorRows(fileError ? job.getTotalRows() : (int)invalidRows);
        job.setValidRows(job.getTotalRows()-job.getErrorRows());
    }
    private VocabularyImportResponse response(VocabularyImportJobJpaEntity job,boolean includeData) {
        var summary=summary(job);
        String status=resolvedStatus(job.getStatus(),job.getImportedRows(),summary);
        boolean done = completedSuccessfully(status);
        return new VocabularyImportResponse(job.getId(),job.getModule(),job.getFilename(),job.getActorId(),job.getActorName(),status,
                job.getTotalRows(),job.getValidRows(),job.getErrorRows(),job.getImportedRows(),
                "FAILED".equals(job.getStatus()) ? job.getTotalRows() : job.getErrorRows(),
                "READY".equals(job.getStatus()) && handlers.stream().anyMatch(h -> h.type().id().equals(job.getModule())),done,job.getCreatedAt(),job.getCompletedAt(),
                includeData ? rows(job) : List.of(),includeData ? decode(job.getDiagnostics(),new TypeReference<List<Error>>() {}) : List.of(),summary);
    }
    private Map<String,Object> summary(VocabularyImportJobJpaEntity job) {
        var data=new LinkedHashMap<String,Object>();
        if(job.getSummary()!=null) data.putAll(decode(job.getSummary(),new TypeReference<Map<String,Object>>() {}));
        return data;
    }
    private List<Row> rows(VocabularyImportJobJpaEntity job) { return decode(job.getPayload(),new TypeReference<List<Row>>() {}); }
    private List<Row> sanitizeRows(List<Row> requestedRows,Type type) {
        if(requestedRows==null || requestedRows.isEmpty() || requestedRows.size()>20000)
            throw new LearningException(LearningException.Kind.INVALID,"Bản xem trước phải có từ 1 đến 20.000 dòng dữ liệu.");
        var result=new ArrayList<Row>(requestedRows.size()); var numbers=new HashSet<Integer>();
        for(var row:requestedRows) {
            if(row==null || row.rowNumber()<2 || row.rowNumber()>20001 || !numbers.add(row.rowNumber()))
                throw new LearningException(LearningException.Kind.INVALID,"Số dòng nguồn trong bản xem trước không hợp lệ hoặc bị trùng.");
            var values=new LinkedHashMap<String,String>();
            for(String column:type.columns()) {
                String value=row.values()==null?"":Objects.toString(row.values().get(column),"").strip();
                if(value.length()>10000)
                    throw new LearningException(LearningException.Kind.INVALID,"Mỗi ô trong bản xem trước tối đa 10.000 ký tự.");
                values.put(column,value);
            }
            result.add(new Row(row.rowNumber(),values));
        }
        return List.copyOf(result);
    }
    private static int summarySize(Map<String,Object> summary,String key) {
        Object value=summary.get(key); return value instanceof Collection<?> items?items.size():0;
    }
    private static boolean completedSuccessfully(String status) {
        return Set.of("IMPORTED","IMPORTED_WITH_SKIPS","SKIPPED").contains(status);
    }
    private static String resolvedStatus(String storedStatus,int importedRows,Map<String,Object> summary) {
        if(!"IMPORTED".equals(storedStatus)) return storedStatus;
        int skipped=summarySize(summary,"duplicateWords")+summarySize(summary,"duplicateRegionalVariants");
        if(skipped==0) return storedStatus;
        return importedRows==0?"SKIPPED":"IMPORTED_WITH_SKIPS";
    }
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
