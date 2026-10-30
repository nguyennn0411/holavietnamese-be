package com.sep490.backend.modules.learning.importing.web;
import com.sep490.backend.modules.learning.authoring.application.ActivityService;
import com.sep490.backend.modules.learning.authoring.application.CourseContentService;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;



import static com.sep490.backend.modules.learning.importing.web.WebsiteImportModel.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.common.exception.ContentException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebsiteImportService {
  private final WebsiteCourseExtractor extractor;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final ActivityService activities;
  private final RemoteMediaLibraryService mediaLibrary;
  private final SafeWebFetcher safeWeb;
  private final Clock clock;

  @Transactional
  public Project analyze(AnalyzeRequest request, LearnerPrincipal actor) {
    requireActor(actor);
    String normalized = normalizeUrl(request.sourceUrl());
    String fingerprint = sha256(request.mode() + "\n" + normalized);
    Project existing = reusable(fingerprint, actor.id());
    if (existing != null) return existing;
    WebsiteCourseExtractor.Extraction result;
    try { result = extractor.extractUrl(normalized, request.mode()); }
    catch (IllegalArgumentException failure) { throw ContentException.invalid(failure.getMessage()); }
    return store(actor.id(), request.mode(), normalized, null, request.targetCourseId(), fingerprint, result);
  }

  @Transactional
  public Project analyzeFile(byte[] bytes, String filename, Long targetCourseId, LearnerPrincipal actor) {
    requireActor(actor);
    String fingerprint = sha256(bytes);
    Project existing = reusable(fingerprint, actor.id());
    if (existing != null) return existing;
    WebsiteCourseExtractor.Extraction result;
    try { result = extractor.extractFile(bytes, safeFilename(filename)); }
    catch (IllegalArgumentException failure) { throw ContentException.invalid(failure.getMessage()); }
    return store(actor.id(), "FILE", null, safeFilename(filename), targetCourseId, fingerprint, result);
  }

  @Transactional(readOnly = true)
  public Project detail(String id) { return project(row(id)); }

  @Transactional(readOnly = true)
  public List<Project> history(int limit) {
    return jdbc.queryForList("SELECT * FROM website_import_jobs ORDER BY created_at DESC,id DESC LIMIT ?", Math.max(1, Math.min(limit, 100)))
        .stream().map(this::project).toList();
  }

  @Transactional
  public Project update(String id, UpdateRequest request, LearnerPrincipal actor) {
    requireActor(actor); Map<String,Object> job = rowForUpdate(id); assertOwner(job, actor.id());
    if ("SAVED".equals(job.get("status"))) throw ContentException.conflict("IMPORT_ALREADY_SAVED", "Bản import đã được lưu và không thể chỉnh sửa.");
    List<ModuleDraft> modules = normalizeAndValidate(request.modules());
    jdbc.update("UPDATE website_import_jobs SET payload=?,status='REVIEW',updated_at=? WHERE id=?",
        encode(modules), Timestamp.from(clock.instant()), id);
    return detail(id);
  }

  @Transactional
  public Project retryMedia(String id, LearnerPrincipal actor) {
    requireActor(actor); Map<String,Object> job = rowForUpdate(id); assertOwner(job, actor.id());
    if ("SAVED".equals(job.get("status"))) throw ContentException.conflict("IMPORT_ALREADY_SAVED", "Bản import đã lưu; hãy xử lý media trong Course Builder.");
    List<ModuleDraft> modules = decode(job.get("payload"), new TypeReference<>() {});
    List<ModuleDraft> checked = new ArrayList<>();
    for (ModuleDraft module : modules) {
      List<LessonDraft> lessons = new ArrayList<>();
      for (LessonDraft lesson : module.lessons()) {
        List<BlockDraft> blocks = new ArrayList<>();
        for (BlockDraft block : lesson.blocks()) {
          if (Set.of("IMAGE","AUDIO","VIDEO").contains(block.type()) && block.mediaUrl()!=null) {
            MediaProbe probe = safeWeb.probe(block.mediaUrl());
            String status = "AVAILABLE_EXTERNAL".equals(probe.status()) ? "PENDING_RIGHTS" : probe.status();
            block = new BlockDraft(block.key(),block.type(),block.orderIndex(),block.title(),block.html(),block.text(),
                block.mediaUrl(),block.originalUrl(),block.sourcePageUrl(),firstNonBlank(probe.mimeType(),block.mimeType()),
                block.width(),block.height(),block.altText(),block.caption(),block.attribution(),block.license(),status,
                block.reviewRequired() || !"AVAILABLE_EXTERNAL".equals(probe.status()));
          }
          blocks.add(block);
        }
        lessons.add(new LessonDraft(lesson.key(),lesson.title(),lesson.sourcePageUrl(),lesson.selected(),blocks));
      }
      checked.add(new ModuleDraft(module.key(),module.title(),module.selected(),lessons));
    }
    jdbc.update("UPDATE website_import_jobs SET payload=?,updated_at=? WHERE id=?",encode(checked),Timestamp.from(clock.instant()),id);
    return detail(id);
  }

  @Transactional
  public Project save(String id, SaveRequest request, LearnerPrincipal actor) {
    requireActor(actor); Map<String,Object> job = rowForUpdate(id); assertOwner(job, actor.id());
    if ("SAVED".equals(job.get("status"))) return project(job);
    if (!request.rightsConfirmed())
      throw ContentException.invalid("Admin phải xác nhận có quyền sử dụng nội dung và media trước khi lưu nháp.");
    List<ModuleDraft> modules = decode(job.get("payload"), new TypeReference<>() {});
    List<ModuleDraft> selected = modules.stream().filter(ModuleDraft::selected)
        .map(m -> new ModuleDraft(m.key(),m.title(),true,m.lessons().stream().filter(LessonDraft::selected).toList()))
        .filter(m -> !m.lessons().isEmpty()).toList();
    if (selected.isEmpty()) throw ContentException.invalid("Chọn ít nhất một bài học để import.");
    String fingerprint = Objects.toString(job.get("source_fingerprint"));
    long already = jdbc.queryForObject("SELECT count(*) FROM website_import_provenance WHERE source_fingerprint=?", Long.class, fingerprint);
    if (already > 0) throw ContentException.conflict("SOURCE_ALREADY_IMPORTED", "Nguồn này đã được lưu trước đó; mở lần import cũ để tránh tạo bài trùng.");

    var diagnostics = new ArrayList<Diagnostic>(decode(job.get("diagnostics"), new TypeReference<List<Diagnostic>>() {}));
    selected = copyConfirmedMedia(selected, diagnostics);
    jdbc.update("UPDATE website_import_jobs SET payload=?,diagnostics=?,updated_at=? WHERE id=?",
        encode(selected), encode(diagnostics), Timestamp.from(clock.instant()), id);

    Long target = number(job.get("target_course_id"));
    long courseId = target == null ? createCourse(Objects.toString(job.get("title")), actor.id(), selected) : target;
    String license = firstNonBlank(request.license(), text(job.get("license")));
    String attribution = firstNonBlank(request.attribution(), text(job.get("attribution")));
    for (ModuleDraft module : selected) {
      long moduleId = courses.addModule(courseId, new ContentRequests.Module(module.title(), "Imported source content", module.title(), "Nội dung nhập từ nguồn đã dẫn"));
      jdbc.update("UPDATE course_units SET status='DRAFT' WHERE id=?", moduleId);
      for (LessonDraft lesson : module.lessons()) {
        String uniqueKey = id + ":" + lesson.key();
        String slug = slug(lesson.title()) + "-" + shortId(uniqueKey);
        long lessonId = courses.addLesson(moduleId, new ContentRequests.Lesson(
            code("WEB", shortId(uniqueKey)), slug, lesson.title(), "Imported exactly from " + Objects.toString(lesson.sourcePageUrl(), Objects.toString(job.get("source_filename"), "uploaded file")),
            lessonType(lesson.title()), Math.max(1, Math.min(120, lesson.blocks().size() * 2)), List.of(),
            "Review the source-faithful imported material before publishing.", lesson.title(), "Nội dung nhập nguyên bản; cần biên tập kiểm duyệt.", "Kiểm tra nội dung và nguồn trước khi xuất bản."));
        for (BlockDraft block : lesson.blocks()) {
          ObjectNode content = content(block, license, attribution);
          long activityId = activities.create(lessonId, new ContentRequests.Activity(activityType(block.type()),
              activityTitle(block), reviewInstruction(block), content, true, BigDecimal.ZERO, "DRAFT", null, List.of(),
              activityTitle(block), reviewInstruction(block)));
          jdbc.update("INSERT INTO website_import_provenance(import_job_id,lesson_id,activity_id,block_key,source_page_url,original_url,attribution,license,import_status,source_fingerprint) VALUES(?,?,?,?,?,?,?,?,?,?)",
              id, lessonId, activityId, lesson.key()+":"+block.key(), block.sourcePageUrl(), block.originalUrl(),
              firstNonBlank(block.attribution(), attribution), firstNonBlank(block.license(), license), block.importStatus(), fingerprint);
        }
      }
    }
    Stats saved = stats(selected);
    diagnostics.add(new Diagnostic("INFO","SAVED_COUNTS","Đã lưu nháp " + saved.modules() + " module, " + saved.lessons() + " lesson và " + saved.blocks() + " content block.",null));
    Instant now = clock.instant();
    jdbc.update("UPDATE website_import_jobs SET status='SAVED',rights_confirmed=true,license=?,attribution=?,result_course_id=?,diagnostics=?,updated_at=?,completed_at=? WHERE id=?",
        license, attribution, courseId, encode(diagnostics), Timestamp.from(now), Timestamp.from(now), id);
    return detail(id);
  }

  private Project store(long actor, String mode, String sourceUrl, String filename, Long target, String fingerprint,
      WebsiteCourseExtractor.Extraction result) {
    if (target != null && jdbc.queryForObject("SELECT count(*) FROM courses WHERE id=?", Long.class, target) != 1)
      throw ContentException.missing("TARGET_COURSE_NOT_FOUND");
    String id = UUID.randomUUID().toString(); Instant now = clock.instant();
    jdbc.update("INSERT INTO website_import_jobs(id,actor_id,mode,status,source_url,source_filename,source_fingerprint,target_course_id,title,author,license,attribution,payload,diagnostics,created_at,updated_at) VALUES(?,?,?,'ANALYZED',?,?,?,?,?,?,?,?,?,?,?,?)",
        id, actor, mode, sourceUrl, filename, fingerprint, target, limit(result.title(),500), limit(result.author(),500),
        limit(result.license(),500), result.attribution(), encode(normalizeAndValidate(result.modules())), encode(result.diagnostics()), Timestamp.from(now), Timestamp.from(now));
    return detail(id);
  }

  private Project reusable(String fingerprint, long actor) {
    var rows = jdbc.queryForList("SELECT * FROM website_import_jobs WHERE source_fingerprint=? AND actor_id=? AND status<>'FAILED' ORDER BY created_at DESC LIMIT 1", fingerprint, actor);
    return rows.isEmpty() ? null : project(rows.getFirst());
  }

  private Project project(Map<String,Object> row) {
    List<ModuleDraft> modules = decode(row.get("payload"), new TypeReference<>() {});
    List<Diagnostic> diagnostics = decode(row.get("diagnostics"), new TypeReference<>() {});
    Stats stats = stats(modules);
    String status = text(row.get("status"));
    return new Project(text(row.get("id")), status, text(row.get("mode")), text(row.get("source_url")), text(row.get("source_filename")),
        text(row.get("title")), text(row.get("author")), text(row.get("license")), text(row.get("attribution")),
        number(row.get("target_course_id")), number(row.get("result_course_id")), bool(row.get("rights_confirmed")),
        !"SAVED".equals(status) && stats.lessons() > 0, stats, modules, diagnostics,
        instant(row.get("created_at")), instant(row.get("updated_at")), instant(row.get("completed_at")));
  }

  private static Stats stats(List<ModuleDraft> modules) {
    int lesson=0,block=0,heading=0,paragraph=0,image=0,audio=0,video=0,table=0,review=0;
    for (ModuleDraft m:modules) for (LessonDraft l:m.lessons()) { lesson++; for(BlockDraft b:l.blocks()) {
      block++; switch(b.type()){case "HEADING"->heading++;case "PARAGRAPH","RICH_TEXT"->paragraph++;case "IMAGE"->image++;case "AUDIO"->audio++;case "VIDEO"->video++;case "TABLE"->table++;default->{}}
      if(b.reviewRequired())review++;
    }}
    return new Stats(modules.size(),lesson,block,heading,paragraph,image,audio,video,table,review);
  }

  private ObjectNode content(BlockDraft block, String license, String attribution) {
    ObjectNode out = json.createObjectNode(); out.put("importedExact", true); out.put("importedBlockType", block.type());
    put(out,"body",block.text()); put(out,"html",block.html()); put(out,"sourcePageUrl",block.sourcePageUrl());
    put(out,"originalUrl",block.originalUrl()); put(out,"caption",block.caption()); put(out,"altText",block.altText());
    put(out,"attribution",firstNonBlank(block.attribution(),attribution)); put(out,"license",firstNonBlank(block.license(),license));
    put(out,"importStatus",block.importStatus()); put(out,"mimeType",block.mimeType());
    if(block.width()!=null)out.put("width",block.width()); if(block.height()!=null)out.put("height",block.height());
    if("IMAGE".equals(block.type()))put(out,"imageUrl",block.mediaUrl());
    if("AUDIO".equals(block.type()))put(out,"audioUrl",block.mediaUrl());
    if("VIDEO".equals(block.type())) {
      if (block.reviewRequired()) put(out,"embedUrl",block.mediaUrl());
      else put(out,"videoUrl",block.mediaUrl());
    }
    return out;
  }

  private List<ModuleDraft> copyConfirmedMedia(List<ModuleDraft> modules, List<Diagnostic> diagnostics) {
    List<ModuleDraft> output = new ArrayList<>();
    for (ModuleDraft module : modules) {
      List<LessonDraft> lessons = new ArrayList<>();
      for (LessonDraft lesson : module.lessons()) {
        List<BlockDraft> blocks = new ArrayList<>();
        for (BlockDraft block : lesson.blocks()) {
          if (Set.of("IMAGE","AUDIO","VIDEO").contains(block.type()) && block.mediaUrl()!=null
              && !block.mediaUrl().contains("res.cloudinary.com")
              && (!"VIDEO".equals(block.type()) || !block.reviewRequired())) {
            var imported = mediaLibrary.copy(block.mediaUrl(), block.type());
            boolean failed = !"IMPORTED_MEDIA".equals(imported.status());
            if (failed) diagnostics.add(new Diagnostic("WARNING","MEDIA_UPLOAD_FAILED",
                "Không sao chép được media " + block.mediaUrl() + ": " + Objects.toString(imported.error(),"không rõ lỗi"), block.sourcePageUrl()));
            block = new BlockDraft(block.key(),block.type(),block.orderIndex(),block.title(),block.html(),block.text(),
                imported.mediaUrl(),block.originalUrl(),block.sourcePageUrl(),block.mimeType(),block.width(),block.height(),
                block.altText(),block.caption(),block.attribution(),block.license(),imported.status(),failed || block.reviewRequired());
          }
          blocks.add(block);
        }
        lessons.add(new LessonDraft(lesson.key(),lesson.title(),lesson.sourcePageUrl(),lesson.selected(),blocks));
      }
      output.add(new ModuleDraft(module.key(),module.title(),module.selected(),lessons));
    }
    return output;
  }

  private List<ModuleDraft> normalizeAndValidate(List<ModuleDraft> modules) {
    if(modules==null||modules.isEmpty())throw ContentException.invalid("Cấu trúc import không được để trống.");
    Set<String> keys=new HashSet<>(); List<ModuleDraft> normalized=new ArrayList<>();
    for(ModuleDraft m:modules){if(m==null||m.title()==null||m.title().isBlank()||!keys.add("m:"+m.key()))throw ContentException.invalid("Module không hợp lệ hoặc bị trùng.");
      List<LessonDraft> lessons=new ArrayList<>(); for(LessonDraft l:m.lessons()==null?List.<LessonDraft>of():m.lessons()){
        if(l==null||l.title()==null||l.title().isBlank()||!keys.add("l:"+l.key()))throw ContentException.invalid("Lesson không hợp lệ hoặc bị trùng.");
        List<BlockDraft> blocks=new ArrayList<>(); int bi=0; for(BlockDraft b:l.blocks()==null?List.<BlockDraft>of():l.blocks()){
          if(b==null||b.type()==null||!keys.add("b:"+l.key()+":"+b.key()))throw ContentException.invalid("Content block không hợp lệ hoặc bị trùng.");
          if(!Set.of("HEADING","PARAGRAPH","RICH_TEXT","TABLE","IMAGE","AUDIO","VIDEO").contains(b.type()))
            throw ContentException.invalid("Loại content block không được hỗ trợ: " + b.type());
          if(b.mediaUrl()!=null&&!b.mediaUrl().isBlank()) safeWeb.publicUri(b.mediaUrl());
          String safeHtml=b.html()==null?null:WebsiteCourseExtractor.sanitizeImportedHtml(b.html(),b.sourcePageUrl());
          blocks.add(new BlockDraft(b.key(),b.type(),++bi,b.title(),safeHtml,b.text(),b.mediaUrl(),b.originalUrl(),b.sourcePageUrl(),b.mimeType(),b.width(),b.height(),b.altText(),b.caption(),b.attribution(),b.license(),b.importStatus(),b.reviewRequired()));
        } lessons.add(new LessonDraft(l.key(),limit(l.title(),200),l.sourcePageUrl(),l.selected(),blocks));
      } normalized.add(new ModuleDraft(m.key(),limit(m.title(),200),m.selected(),lessons));
    } return normalized;
  }

  private long createCourse(String title, long actor, List<ModuleDraft> modules) {
    String suffix=UUID.randomUUID().toString().substring(0,8); int lessons=modules.stream().mapToInt(m->m.lessons().size()).sum();
    String safeTitle=limit(title,200);
    return courses.create(new ContentRequests.Course("WEB-"+suffix.toUpperCase(Locale.ROOT),slug(safeTitle)+"-"+suffix,safeTitle,
        "Source-faithful website import. Review attribution, licensing, and every media item before publishing.","A0",null,
        lessons*15,"Imported material remains draft until editorial and rights review is complete.",false,safeTitle,
        "Nội dung nhập từ website; chưa xuất bản và cần kiểm duyệt nguồn, bản quyền, media."),actor);
  }

  private Map<String,Object> row(String id){if(!id.matches("[a-f0-9-]{36}"))throw ContentException.invalid("Mã import không hợp lệ.");var rows=jdbc.queryForList("SELECT * FROM website_import_jobs WHERE id=?",id);if(rows.isEmpty())throw ContentException.missing("WEBSITE_IMPORT_NOT_FOUND");return rows.getFirst();}
  private Map<String,Object> rowForUpdate(String id){if(!id.matches("[a-f0-9-]{36}"))throw ContentException.invalid("Mã import không hợp lệ.");var rows=jdbc.queryForList("SELECT * FROM website_import_jobs WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw ContentException.missing("WEBSITE_IMPORT_NOT_FOUND");return rows.getFirst();}
  private void assertOwner(Map<String,Object> row,long actor){if(number(row.get("actor_id"))!=actor)throw ContentException.forbidden("IMPORT_OWNER_REQUIRED","Chỉ người tạo lần import mới được chỉnh sửa hoặc lưu.");}
  private static void requireActor(LearnerPrincipal actor){if(actor==null||actor.id()==null)throw ContentException.forbidden("AUTH_REQUIRED","Cần đăng nhập Admin.");}
  private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw ContentException.invalid("Không thể lưu dữ liệu import.");}}
  private <T>T decode(Object value,TypeReference<T> type){try{return json.readValue(Objects.toString(value,"[]"),type);}catch(Exception e){throw ContentException.invalid("Dữ liệu import đã lưu không hợp lệ.");}}
  private static void put(ObjectNode o,String key,String value){if(value!=null&&!value.isBlank())o.put(key,value);}
  private static String activityType(String type){return switch(type){case "IMAGE"->"IMAGE";case "AUDIO"->"AUDIO";case "VIDEO"->"VIDEO";default->"TEXT";};}
  private static String lessonType(String title){String t=Objects.toString(title,"").toLowerCase(Locale.ROOT);if(t.contains("vocabulary"))return "VOCABULARY";if(t.contains("grammar"))return "GRAMMAR";if(t.contains("conversation"))return "CONVERSATION";if(t.contains("culture"))return "CULTURE";if(t.contains("practice"))return "REVIEW";return "NORMAL";}
  private static String activityTitle(BlockDraft b){String base=firstNonBlank(b.title(),b.caption(),switch(b.type()){case"HEADING"->"Tiêu đề";case"PARAGRAPH"->"Nội dung";case"TABLE"->"Bảng";case"IMAGE"->"Hình ảnh";case"AUDIO"->"Âm thanh";case"VIDEO"->"Video / nội dung nhúng";default->"Nội dung nguồn";});return limit(base,200);}
  private static String reviewInstruction(BlockDraft b){return b.reviewRequired()?"Cần Admin kiểm tra nguồn, khả năng tải và quyền sử dụng trước khi xuất bản.":"Nội dung được nhập nguyên bản theo thứ tự nguồn; kiểm tra trước khi xuất bản.";}
  private static String normalizeUrl(String value){try{var u=java.net.URI.create(value.trim()).normalize();return new java.net.URI(u.getScheme(),u.getAuthority(),u.getPath(),u.getQuery(),null).toString();}catch(Exception e){throw ContentException.invalid("URL nguồn không hợp lệ.");}}
  private static String safeFilename(String name){String n=Objects.toString(name,"upload.xhtml").replace('\\','/');return limit(n.substring(n.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]",""),255);}
  private static String slug(String value){String s=java.text.Normalizer.normalize(Objects.toString(value,"course"),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('đ','d').replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");return s.isBlank()?"imported-course":limit(s,170);}
  private static String code(String prefix,String key){return limit((prefix+"-"+key).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]","-"),80);}
  private static String shortId(String key){return Integer.toUnsignedString(Objects.toString(key,"").hashCode(),36);}
  private static String sha256(String value){return sha256(value.getBytes(StandardCharsets.UTF_8));}
  private static String sha256(byte[] value){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(value);return java.util.HexFormat.of().formatHex(hash);}catch(Exception e){throw new IllegalStateException(e);}}
  private static String limit(String value,int max){if(value==null)return null;return value.length()<=max?value:value.substring(0,max);}
  private static String text(Object value){return value==null?null:value.toString();}
  private static Long number(Object value){return value instanceof Number n?n.longValue():value==null?null:Long.valueOf(value.toString());}
  private static boolean bool(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
  private static Instant instant(Object value){if(value==null)return null;if(value instanceof Timestamp t)return t.toInstant();if(value instanceof java.time.LocalDateTime t)return t.toInstant(ZoneOffset.UTC);return Instant.parse(value.toString());}
  private static String firstNonBlank(String...values){for(String v:values)if(v!=null&&!v.isBlank())return v.trim();return null;}
}
