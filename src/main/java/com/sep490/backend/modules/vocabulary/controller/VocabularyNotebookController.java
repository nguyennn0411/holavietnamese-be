package com.sep490.backend.modules.vocabulary.controller;

import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyNotebookRequest;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyNotebookResponse;
import com.sep490.backend.modules.vocabulary.service.VocabularyNotebookService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/vocabulary")
@RequiredArgsConstructor
public class VocabularyNotebookController {
    private final VocabularyNotebookService vocabulary;

    /** Lay danh sach theo bo loc cua nguoi dung. */
    @GetMapping
    public List<VocabularyNotebookResponse> list(
            @AuthenticationPrincipal LearnerPrincipal user,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long lessonId) {
        return vocabulary.search(user.id(), search, courseId, lessonId);
    }

    /** Lay chi tiet theo ID va quyen truy cap hien tai. */
    @GetMapping("/{id}")
    public VocabularyNotebookResponse get(
            @AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) {
        return vocabulary.get(user.id(), id);
    }

    /** Them tu vao so ca nhan sau khi kiem tra nguon va quyen truy cap. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VocabularyNotebookResponse add(
            @AuthenticationPrincipal LearnerPrincipal user,
            @Valid @RequestBody VocabularyNotebookRequest request) {
        return vocabulary.add(user.id(), request);
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @PutMapping("/{id}")
    public VocabularyNotebookResponse update(
            @AuthenticationPrincipal LearnerPrincipal user,
            @PathVariable Long id,
            @Valid @RequestBody VocabularyNotebookRequest request) {
        return vocabulary.update(user.id(), id, request);
    }

    /** Xoa muc so tu thuoc nguoi dung va lich su on tap lien quan. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) {
        vocabulary.delete(user.id(), id);
    }
}
