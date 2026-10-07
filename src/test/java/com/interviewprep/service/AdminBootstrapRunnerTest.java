package com.interviewprep.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewprep.config.AdminBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    private static final String PASSWORD = "correct horse battery";

    @Mock
    private UserService userService;

    private void run(AdminBootstrapProperties properties) {
        new AdminBootstrapRunner(properties, userService).run(new DefaultApplicationArguments());
    }

    @Test
    void doesNothingWhenNotConfigured() {
        run(new AdminBootstrapProperties(null, null));
        run(new AdminBootstrapProperties("", ""));

        verifyNoInteractions(userService);
    }

    @Test
    void createsAdminWhenConfigured() {
        when(userService.createAdminIfAbsent("admin@example.com", PASSWORD)).thenReturn(true);

        run(new AdminBootstrapProperties("admin@example.com", PASSWORD));

        verify(userService).createAdminIfAbsent("admin@example.com", PASSWORD);
    }

    @Test
    void isIdempotentWhenAdminAlreadyExists() {
        when(userService.createAdminIfAbsent("admin@example.com", PASSWORD)).thenReturn(false);

        run(new AdminBootstrapProperties("admin@example.com", PASSWORD));
        run(new AdminBootstrapProperties("admin@example.com", PASSWORD));

        // Creation is decided by UserService (see UserServiceTest); the runner only delegates.
        verify(userService, times(2)).createAdminIfAbsent("admin@example.com", PASSWORD);
    }
}
