package com.sep490.backend.modules.learning.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.learning.service.MyCoursesService;
import com.sep490.backend.modules.learning.dto.response.MyCourseResponse;
@RestController @RequiredArgsConstructor
public class MyCoursesController {
    private final MyCoursesService courses;
    @GetMapping("/api/me/courses")
    public List<MyCourseResponse> list(@AuthenticationPrincipal LearnerPrincipal user) { return courses.myCourses(user.id()); }
}
