package com.sep490.backend.common.dto.request;

import jakarta.validation.constraints.NotNull;

/** A reviewer may approve pending content or return it to draft. */
public record ContentReviewRequest(@NotNull Decision decision) {
    public enum Decision { APPROVE, REJECT }
}
