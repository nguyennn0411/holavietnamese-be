package com.sep490.backend.modules.learning.course.web;
import com.sep490.backend.modules.learning.course.application.MyCoursesService;
import com.sep490.backend.modules.learning.course.web.dto.MyCourseResponse;



import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.common.security.LearnerPrincipal;
@RestController @RequiredArgsConstructor
public class MyCoursesController {
    private final MyCoursesService courses;
    @GetMapping("/api/me/courses")
    public List<MyCourseResponse> list(@AuthenticationPrincipal LearnerPrincipal user) { return courses.myCourses(user.id()); }
}
