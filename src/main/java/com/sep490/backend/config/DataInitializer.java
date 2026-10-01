package com.sep490.backend.config;

import com.sep490.backend.entity.Role;
import com.sep490.backend.entity.User;
import com.sep490.backend.repository.RoleRepository;
import com.sep490.backend.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DataInitializer implements CommandLineRunner {

    RoleRepository roleRepository;
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            Role adminRole = initRole("ADMIN", "Quản trị viên hệ thống");
            Role learnerRole = initRole("LEARNER", "Học viên học tiếng Việt");
            initRole("TEACHER", "Giáo viên giảng dạy tiếng Việt");

            // Tạo tài khoản admin mặc định nếu chưa tồn tại
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setEmail("admin@holavietnamese.com");
                admin.setFullName("System Administrator");
                admin.setStatus("ACTIVE");
                admin.setRoles(new HashSet<>(Set.of(adminRole)));
                userRepository.save(admin);
                log.info(">>> Khởi tạo tài khoản admin mặc định: admin / admin123");
            }

            // Tạo tài khoản học viên mẫu nếu chưa tồn tại
            if (!userRepository.existsByUsername("learner")) {
                User learner = new User();
                learner.setUsername("learner");
                learner.setPasswordHash(passwordEncoder.encode("learner123"));
                learner.setEmail("learner@holavietnamese.com");
                learner.setFullName("John Smith");
                learner.setNativeLanguage("en");
                learner.setLearningGoal("Du lịch và giao tiếp hàng ngày");
                learner.setTargetLevel("A1");
                learner.setStatus("ACTIVE");
                learner.setRoles(new HashSet<>(Set.of(learnerRole)));
                userRepository.save(learner);
                log.info(">>> Khởi tạo tài khoản học viên mẫu: learner / learner123");
            }
        } catch (Exception e) {
            log.warn("Không thể seed dữ liệu khởi tạo (Database có thể chưa sẵn sàng): {}", e.getMessage());
        }
    }

    private Role initRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            return roleRepository.save(role);
        });
    }
}
