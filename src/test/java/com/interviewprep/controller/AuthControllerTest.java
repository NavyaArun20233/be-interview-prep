package com.interviewprep.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.WebMvcSecurityTestConfiguration;
import com.interviewprep.dto.auth.LoginRequest;
import com.interviewprep.dto.auth.RegisterRequest;
import com.interviewprep.dto.auth.TokenResponse;
import com.interviewprep.dto.auth.UserResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.exception.EmailAlreadyRegisteredException;
import com.interviewprep.exception.InvalidCredentialsException;
import com.interviewprep.service.AuthService;
import com.interviewprep.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Register and login are public: every request here is sent without a token. */
@WebMvcTest(AuthController.class)
@Import(WebMvcSecurityTestConfiguration.class)
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerReturns201WithProfileAndLocationOfOwnProfile() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(7L, "ada@example.com", Role.USER, NOW));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "  Ada@Example.com ", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/users/me"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).register(new RegisterRequest("Ada@Example.com", PASSWORD));
    }

    @Test
    void registerIgnoresRoleInRequestBody() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(8L, "eve@example.com", Role.USER, NOW));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "eve@example.com", "password": "%s", "role": "ADMIN"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        // RegisterRequest has no role component, so the service cannot be asked for anything but a USER.
        verify(userService).register(new RegisterRequest("eve@example.com", PASSWORD));
    }

    @Test
    void registerRejectsInvalidEmailAndShortPasswordWithoutEchoingThem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "s3cret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message")
                        .value("must be a well-formed email address"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
                        .value("must be between 8 and 72 characters (at most 72 bytes in UTF-8)"))
                .andExpect(content().string(not(containsString("s3cret"))));

        verifyNoInteractions(userService);
    }

    @Test
    void registerRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("must not be blank"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'password')].message").value("must not be blank"));

        verifyNoInteractions(userService);
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() throws Exception {
        // 37 two-byte characters: 37 characters but 74 bytes in UTF-8.
        String password = "é".repeat(37);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ada@example.com", "password": "%s"}
                                """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        verifyNoInteractions(userService);
    }

    @Test
    void registerDuplicateEmailReturns409ProblemDetail() throws Exception {
        when(userService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ada@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/register"));
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        Instant expiresAt = NOW.plusSeconds(900);
        when(authService.login(new LoginRequest("ada@example.com", PASSWORD)))
                .thenReturn(new TokenResponse("header.payload.signature", "Bearer", 900, expiresAt));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ada@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("header.payload.signature"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.expiresAt").value("2026-01-15T10:15:00Z"));
    }

    @Test
    void loginWithBadCredentialsReturns401ProblemDetail() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ada@example.com", "password": "wrong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"));
    }

    @Test
    void loginRejectsBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": " ", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));

        verifyNoInteractions(authService);
    }
}
