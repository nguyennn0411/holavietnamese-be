package com.sep490.backend.modules.learning.progress.port;

/** Read-only capability exposed by the AI module to learning progress. */
public interface AiProgressReader {
    long countCompletedRoleplays(Long userId);
}
