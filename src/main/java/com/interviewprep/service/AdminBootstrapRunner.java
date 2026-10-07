package com.interviewprep.service;

import com.interviewprep.config.AdminBootstrapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN at startup from {@link AdminBootstrapProperties}, since self-registration only creates
 * USERs. Idempotent: an existing account with the same email is left alone. Does nothing when not configured.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final UserService userService;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.configured()) {
            log.debug("Admin bootstrap not configured; skipping");
            return;
        }
        if (userService.createAdminIfAbsent(properties.email(), properties.password())) {
            log.info("Admin bootstrap: created admin account {}", properties.email());
        } else {
            log.info("Admin bootstrap: account {} already exists; nothing to do", properties.email());
        }
    }
}
