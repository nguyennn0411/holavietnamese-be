package com.sep490.backend.learning.shared;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** SQL identifiers are internal constants; user values always use prepared parameters. */
@Repository
@RequiredArgsConstructor
public class ContentStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public Map<String, Object> one(String sql, Object... args) {
    var rows = rows(sql, args);
    if (rows.isEmpty()) throw ContentException.missing("CONTENT_NOT_FOUND");
    return rows.getFirst();
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, n) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          var meta = rs.getMetaData();
          for (int i = 1; i <= meta.getColumnCount(); i++)
            row.put(camel(meta.getColumnLabel(i).toLowerCase(Locale.ROOT)), rs.getObject(i));
          // Legacy columns keep their values; API consumers receive unambiguous English aliases.
          for (String field : List.of("title", "description", "instruction", "explanation", "commonMistakes",
              "learningObjective", "prompt", "quizTitle", "courseTitle", "moduleTitle")) {
            if (row.containsKey(field)) row.putIfAbsent(field + "En", row.get(field));
          }
          return row;
        },
        args);
  }

  public long insert(String sql, Object... args) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var s = c.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
          return s;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public JsonNode json(Object value) {
    try {
      return value == null ? mapper.nullNode() : mapper.readTree(value.toString());
    } catch (Exception e) {
      throw ContentException.invalid("Invalid JSON content.");
    }
  }

  public String encode(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw ContentException.invalid("Invalid JSON content.");
    }
  }

  public static long id(Map<String, Object> r, String key) {
    return ((Number) r.get(key)).longValue();
  }

  public static boolean bool(Object v) {
    return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue() != 0;
  }

  private static String camel(String s) {
    var b = new StringBuilder();
    boolean up = false;
    for (char c : s.toCharArray()) {
      if (c == '_') up = true;
      else {
        b.append(up ? Character.toUpperCase(c) : c);
        up = false;
      }
    }
    return b.toString();
  }
}
