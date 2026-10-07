package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewprep.dto.auth.LoginRequest;
import com.interviewprep.dto.auth.TokenResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.entity.User;
import com.interviewprep.exception.InvalidCredentialsException;
import com.interviewprep.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String PASSWORD = "correct horse battery";

    private final PasswordEncoder passwordEncoder = spy(PasswordEncoderFactories.createDelegatingPasswordEncoder());

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
        clearInvocations(passwordEncoder);
    }

    private User storedUser(long id, Role role) {
        User user = new User("ada@example.com", passwordEncoder.encode(PASSWORD), role, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        clearInvocations(passwordEncoder);
        return user;
    }

    @Test
    void loginWithCorrectPasswordIssuesTokenForUser() {
        User user = storedUser(7L, Role.ADMIN);
        TokenResponse token = new TokenResponse("token", "Bearer", 900, NOW.plusSeconds(900));
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(7L, "ada@example.com", Role.ADMIN)).thenReturn(token);

        assertThat(authService.login(new LoginRequest(" ADA@example.com ", PASSWORD)))
                .isEqualTo(token);
    }

    @Test
    void wrongPasswordAndUnknownEmailFailIdenticallyAndBothCheckAPassword() {
        User user = storedUser(7L, Role.USER);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Throwable wrongPassword =
                catchThrowable(() -> authService.login(new LoginRequest("ada@example.com", "wrong password")));
        Throwable unknownEmail =
                catchThrowable(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)));

        assertThat(wrongPassword)
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class).hasMessage(wrongPassword.getMessage());
        // An unknown email still costs one bcrypt check, so timing does not reveal which emails exist.
        verify(passwordEncoder).matches(eq("wrong password"), anyString());
        verify(passwordEncoder).matches(eq(PASSWORD), anyString());
        verifyNoInteractions(tokenService);
    }

    @Test
    void passwordLongerThan72BytesIsRejectedWithoutLookup() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "a".repeat(73))))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userRepository, tokenService);
    }
}
