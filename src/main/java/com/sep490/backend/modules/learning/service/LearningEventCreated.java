package com.sep490.backend.modules.learning.service;

/** Published inside the business transaction; handled only after that transaction commits. */
public record LearningEventCreated(String eventKey) {}
