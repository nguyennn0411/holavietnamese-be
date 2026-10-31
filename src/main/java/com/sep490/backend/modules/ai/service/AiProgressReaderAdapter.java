package com.sep490.backend.modules.ai.service;

import com.sep490.backend.modules.ai.repository.AiSessionRepository;
import com.sep490.backend.modules.learning.progress.port.AiProgressReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiProgressReaderAdapter implements AiProgressReader {
    private final AiSessionRepository aiSessionRepository;

    @Override
    public long countCompletedRoleplays(Long userId) {
        return aiSessionRepository.countByUserIdAndStatus(userId, "COMPLETED");
    }
}
