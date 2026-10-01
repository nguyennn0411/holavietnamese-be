package com.sep490.backend.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.MyCoursesService;
import com.sep490.backend.dto.response.MyCourseResponse;
@RestController @RequiredArgsConstructor
public class MyCoursesController {
    private final MyCoursesService courses;
    @GetMapping("/api/me/courses")
    public List<MyCourseResponse> list(@AuthenticationPrincipal LearnerPrincipal user) { return courses.myCourses(user.id()); }
}
