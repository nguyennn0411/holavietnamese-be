package com.sep490.backend.modules.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.auth.security.CustomJwtDecoder;
import com.sep490.backend.modules.auth.service.NotificationService;
import com.sep490.backend.modules.auth.service.NotificationStreamHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

class NotificationControllerValidationTests {

    private NotificationService notificationService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        NotificationController controller = new NotificationController(
                notificationService,
                mock(NotificationStreamHub.class),
                mock(UserRepository.class),
                mock(CustomJwtDecoder.class),
                mock(NotificationProperties.class));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        MethodValidationPostProcessor validation = new MethodValidationPostProcessor();
        validation.setValidator(validator);
        validation.setProxyTargetClass(true);
        validation.afterPropertiesSet();
        Object validatedController = validation.postProcessAfterInitialization(
                controller, "notificationController");

        mvc = MockMvcBuilders.standaloneSetup(validatedController)
                .setValidator(validator)
                .build();
    }

    @Test
    void blankTestTitleReturnsStableInvalidRequestError() throws Exception {
        mvc.perform(post("/api/notifications/test")
                        .param("title", "   ")
                        .param("message", "Nội dung hợp lệ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5004));

        verifyNoInteractions(notificationService);
    }

    @Test
    void oversizedTestMessageReturnsStableInvalidRequestError() throws Exception {
        mvc.perform(post("/api/notifications/test")
                        .param("title", "Tiêu đề hợp lệ")
                        .param("message", "x".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5004));

        verifyNoInteractions(notificationService);
    }

    @Test
    void nonPositiveNotificationIdReturnsStableInvalidRequestError() throws Exception {
        mvc.perform(delete("/api/notifications/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5004));

        verifyNoInteractions(notificationService);
    }
}
