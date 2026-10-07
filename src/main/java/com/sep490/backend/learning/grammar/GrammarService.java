package com.sep490.backend.learning.grammar;

import com.sep490.backend.learning.shared.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrammarService {
  private final ContentStore db;

  public PageResponse<Map<String, Object>> list(Map<String, String> p, boolean admin) {
    return ContentList.query(
        db,
        "SELECT g.*,(SELECT count(*) FROM grammar_examples e WHERE e.grammar_topic_id=g.id) AS"
            + " example_count",
        "FROM grammar_topics g",
        admin ? "1=1" : "g.status='PUBLISHED'",
        "g.title,g.title_vi,g.explanation,g.explanation_vi,g.structure_pattern",
        Map.of("level", "g.level", "status", "g.status"),
        Map.of("id", "g.id", "title", "g.title", "updatedAt", "g.updated_at"),
        p);
  }

  public Map<String, Object> detail(long id, boolean admin) {
    var g =
        db.one(
            "SELECT * FROM grammar_topics WHERE id=?" + (admin ? "" : " AND status='PUBLISHED'"),
            id);
    g.put(
        "examples",
        db.rows(
            "SELECT * FROM grammar_examples WHERE grammar_topic_id=? ORDER BY order_index", id));
    g.put(
        "relatedLessons",
        db.rows(
            "SELECT DISTINCT l.id,l.title,l.title_vi FROM lessons l JOIN lesson_activities a ON"
                + " a.lesson_id=l.id JOIN activity_grammar_topics ag ON ag.activity_id=a.id JOIN"
                + " courses c ON c.id=l.course_id WHERE ag.grammar_topic_id=?"
                + (admin
                    ? ""
                    : " AND l.published=true AND a.status='PUBLISHED' AND c.status='PUBLISHED'"),
            id));
    return g;
  }

  @Transactional
  public long create(ContentRequests.Grammar r) {
    ContentRules.member(r.level(), ContentRules.LEVELS);
    long id =
        db.insert(
            "INSERT INTO"
                + " grammar_topics(code,slug,title,structure_pattern,explanation,common_mistakes,level,status)"
                + " VALUES(?,?,?,?,?,?,?,'DRAFT')",
            r.code(),
            r.slug(),
            r.title(),
            r.structurePattern(),
            r.explanation(),
            r.commonMistakes(),
            r.level());
    examples(id, r);
    return id;
  }

  @Transactional
  public void edit(long id, ContentRequests.Grammar r) {
    db.one("SELECT id FROM grammar_topics WHERE id=? FOR UPDATE", id);
    ContentRules.member(r.level(), ContentRules.LEVELS);
    db.update(
        "UPDATE grammar_topics SET"
            + " code=?,slug=?,title=?,structure_pattern=?,explanation=?,common_mistakes=?,level=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        r.code(),
        r.slug(),
        r.title(),
        r.structurePattern(),
        r.explanation(),
        r.commonMistakes(),
        r.level(),
        id);
    db.update("DELETE FROM grammar_examples WHERE grammar_topic_id=?", id);
    examples(id, r);
  }

  private void examples(long id, ContentRequests.Grammar r) {
    db.update("UPDATE grammar_topics SET description=?,notes=? WHERE id=?", r.description(), r.notes(), id);
    db.update("UPDATE grammar_topics SET title_vi=COALESCE(?,title_vi),description_vi=COALESCE(?,description_vi),explanation_vi=COALESCE(?,explanation_vi),common_mistakes_vi=COALESCE(?,common_mistakes_vi),structure_pattern_en=COALESCE(?,structure_pattern_en) WHERE id=?",
        r.titleVi(),r.descriptionVi(),r.explanationVi(),r.commonMistakesVi(),r.structurePatternEn(),id);
    for (int i = 0; i < r.examples().size(); i++) {
      var e = r.examples().get(i);
      db.insert(
          "INSERT INTO"
              + " grammar_examples(grammar_topic_id,vietnamese_text,translation,explanation,order_index)"
              + " VALUES(?,?,?,?,?)",
          id,
          e.vietnameseText(),
          e.translation(),
          e.explanation(),
          i + 1);
    }
  }

  @Transactional
  public void status(long id, String status) {
    ContentRules.member(status, Set.of("DRAFT", "PUBLISHED", "ARCHIVED"));
    db.one("SELECT id FROM grammar_topics WHERE id=? FOR UPDATE", id);
    db.update(
        "UPDATE grammar_topics SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
  }
}
