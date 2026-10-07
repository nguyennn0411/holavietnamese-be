package com.sep490.backend.learning.shared;

import java.util.*;

/** Bounded database pagination. Filter/sort column names are supplied by feature code only. */
public final class ContentList {
  private ContentList() {}

  public static PageResponse<Map<String, Object>> query(
      ContentStore db,
      String select,
      String from,
      String base,
      String searchColumns,
      Map<String, String> filters,
      Map<String, String> allowed,
      Map<String, String> params) {
    int page = number(params.get("page"), 0, 0, 1000000),
        size = number(params.get("size"), 20, 1, 100);
    var where = new StringBuilder(base);
    List<Object> args = new ArrayList<>();
    if (params.get("q") != null && !params.get("q").isBlank()) {
      where.append(" AND (");
      var cols = searchColumns.split(",");
      for (int i = 0; i < cols.length; i++) {
        if (i > 0) where.append(" OR ");
        where.append("LOWER(").append(cols[i]).append(") LIKE ?");
        args.add("%" + params.get("q").toLowerCase(Locale.ROOT) + "%");
      }
      where.append(")");
    }
    filters.forEach(
        (param, column) -> {
          if (params.get(param) != null && !params.get(param).isBlank()) {
            where.append(" AND ").append(column).append("=?");
            args.add(params.get(param));
          }
        });
    long total = db.count("SELECT count(*) " + from + " WHERE " + where, args.toArray());
    String[] sort = params.getOrDefault("sort", "id,desc").split(",");
    String column = allowed.get(sort[0]);
    if (column == null
        || sort.length > 2
        || (sort.length == 2 && !Set.of("asc", "desc").contains(sort[1].toLowerCase(Locale.ROOT))))
      throw ContentException.invalid("Unsupported sort.");
    args.add(size);
    args.add((long) page * size);
    var rows =
        db.rows(
            select
                + " "
                + from
                + " WHERE "
                + where
                + " ORDER BY "
                + column
                + (sort.length == 2 && sort[1].equalsIgnoreCase("asc") ? " ASC" : " DESC")
                + " LIMIT ? OFFSET ?",
            args.toArray());
    return PageResponse.of(rows, page, size, total);
  }

  private static int number(String s, int fallback, int min, int max) {
    try {
      int n = s == null ? fallback : Integer.parseInt(s);
      if (n < min || n > max) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException e) {
      throw ContentException.invalid("Invalid pagination.");
    }
  }
}
