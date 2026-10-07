package com.sep490.backend.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.CourseService;
import com.sep490.backend.dto.response.*;
@RestController @RequestMapping("/api/courses") @RequiredArgsConstructor
public class CourseController {
    private final CourseService courses;
    private final com.sep490.backend.learning.course.CourseContentService content;
    @GetMapping public Object list(@RequestParam java.util.Map<String,String> params) { return params.containsKey("page") ? content.list(params,false) : content.list(java.util.Map.of("size","100"),false).content(); }
    @GetMapping("/{id}") public Object detail(@PathVariable Long id,@AuthenticationPrincipal LearnerPrincipal user) { return content.detail(id,user==null?null:user.id(),false); }
    @GetMapping("/{id}/enrollment-status")
    public ResponseEntity<EnrollmentResponse> status(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) {
        return courses.enrollmentStatus(user.id(), id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @PostMapping("/{id}/enroll") @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse enroll(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return courses.enroll(user.id(), id); }
}
