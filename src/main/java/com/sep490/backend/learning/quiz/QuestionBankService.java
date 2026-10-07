package com.sep490.backend.learning.quiz;

import static com.sep490.backend.learning.shared.ContentStore.*;

import com.sep490.backend.learning.shared.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionBankService {
  private final ContentStore db;
  private final QuestionScoring scoring;

  public PageResponse<Map<String, Object>> list(Map<String, String> p) {
    return ContentList.query(
        db,
        "SELECT q.*",
        "FROM questions q",
        "1=1",
        "q.prompt,q.topic",
        Map.of(
            "questionType",
            "q.question_type",
            "difficulty",
            "q.difficulty",
            "topic",
            "q.topic",
            "status",
            "q.status"),
        Map.of("id", "q.id", "updatedAt", "q.updated_at"),
        p);
  }

  public Map<String, Object> detail(long id) {
    var q = db.one("SELECT * FROM questions WHERE id=?", id);
    var versions =
        db.rows(
            "SELECT * FROM question_versions WHERE question_id=? ORDER BY version_number DESC", id);
    for (var v : versions) {
      v.put("options", db.json(v.remove("optionsSnapshotJson")));
      v.put("correctAnswerJson", db.json(v.get("correctAnswerJson")));
    }
    q.put("versions", versions);
    return q;
  }

  @Transactional
  public long create(ContentRequests.Question r) {
    validate(r);
    long id =
        db.insert(
            "INSERT INTO"
                + " questions(question_type,prompt,explanation,difficulty,topic,image_url,audio_url,status)"
                + " VALUES(?,?,?,?,?,?,?,?)",
            r.questionType(),
            r.prompt(),
            r.explanation(),
            r.difficulty(),
            r.topic(),
            r.imageUrl(),
            r.audioUrl(),
            status(r));
    snapshot(id, 1, r);
    return id;
  }

  @Transactional
  public void edit(long id, ContentRequests.Question r) {
    var q = db.one("SELECT * FROM questions WHERE id=? FOR UPDATE", id);
    validate(r);
    long v = id(q, "currentVersion") + 1;
    db.update(
        "UPDATE questions SET"
            + " question_type=?,prompt=?,explanation=?,difficulty=?,topic=?,image_url=?,audio_url=?,status=?,current_version=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        r.questionType(),
        r.prompt(),
        r.explanation(),
        r.difficulty(),
        r.topic(),
        r.imageUrl(),
        r.audioUrl(),
        status(r),
        v,
        id);
    snapshot(id, v, r);
  }

  private String status(ContentRequests.Question r) {
    return r.status() == null ? "DRAFT" : r.status();
  }

  private void validate(ContentRequests.Question r) {
    ContentRules.member(r.difficulty(), Set.of("EASY", "MEDIUM", "HARD"));
    ContentRules.member(status(r), ContentRules.STATUSES);
    ContentRules.url(r.imageUrl());
    ContentRules.url(r.audioUrl());
    scoring.validate(r.questionType(), r.options(), r.correctAnswerJson());
  }

  private void snapshot(long id, long v, ContentRequests.Question r) {
    db.update("UPDATE questions SET prompt_vi=COALESCE(?,prompt_vi),explanation_vi=COALESCE(?,explanation_vi) WHERE id=?",r.promptVi(),r.explanationVi(),id);
    var bilingual = db.one("SELECT prompt_vi,explanation_vi FROM questions WHERE id=?",id);
    db.insert(
        "INSERT INTO"
            + " question_versions(question_id,version_number,question_type,prompt,explanation,difficulty,image_url,audio_url,correct_answer_json,options_snapshot_json,prompt_vi,explanation_vi)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        v,
        r.questionType(),
        r.prompt(),
        r.explanation(),
        r.difficulty(),
        r.imageUrl(),
        r.audioUrl(),
        db.encode(r.correctAnswerJson()),
        db.encode(scoring.safeOptions(r.options())),bilingual.get("promptVi"),bilingual.get("explanationVi"));
  }
}
