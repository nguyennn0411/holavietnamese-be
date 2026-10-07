package com.sep490.backend.learning.shared;

import java.util.*;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    long totalPages,
    boolean first,
    boolean last) {
  public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
    return new PageResponse<>(
        items,
        page,
        size,
        total,
        (total + size - 1) / size,
        page == 0,
        (long) (page + 1) * size >= total);
  }
}
