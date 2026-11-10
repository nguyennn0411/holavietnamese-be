package com.sep490.backend.support;

import com.sep490.backend.common.security.LearnerPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class ContentApiTestSupport {
    protected static final LearnerPrincipal LEARNER =
            new LearnerPrincipal(9001L, "learner@test.local", "", true, "LEARNER");
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
}
