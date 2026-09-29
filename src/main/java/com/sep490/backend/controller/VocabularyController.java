package com.sep490.backend.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.VocabularyService;
import com.sep490.backend.dto.request.VocabularyRequest;
import com.sep490.backend.dto.response.VocabularyResponse;
@RestController @RequestMapping("/api/me/vocabulary") @RequiredArgsConstructor
public class VocabularyController {
    private final VocabularyService vocabulary;
    @GetMapping
    public List<VocabularyResponse> list(@AuthenticationPrincipal LearnerPrincipal user,
        @RequestParam(defaultValue = "") String search, @RequestParam(required = false) Long courseId, @RequestParam(required = false) Long lessonId) {
        return vocabulary.search(user.id(), search, courseId, lessonId);
    }
    @GetMapping("/{id}")
    public VocabularyResponse get(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return vocabulary.get(user.id(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public VocabularyResponse add(@AuthenticationPrincipal LearnerPrincipal user, @Valid @RequestBody VocabularyRequest request) { return vocabulary.add(user.id(), request); }
    @PutMapping("/{id}")
    public VocabularyResponse update(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id, @Valid @RequestBody VocabularyRequest request) { return vocabulary.update(user.id(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { vocabulary.delete(user.id(), id); }
}
