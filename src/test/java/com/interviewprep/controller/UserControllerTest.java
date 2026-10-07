package com.interviewprep.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.WebMvcSecurityTestConfiguration;
import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.auth.UserResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.exception.UserNotFoundException;
import com.interviewprep.service.UserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@Import(WebMvcSecurityTestConfiguration.class)
class UserControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static RequestPostProcessor tokenFor(long userId, String role) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void meReturnsProfileOfTokenSubject() throws Exception {
        when(userService.get(7L)).thenReturn(new UserResponse(7L, "ada@example.com", Role.USER, NOW));

        mockMvc.perform(get("/api/v1/users/me").with(tokenFor(7L, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void meForDeletedUserReturns404() throws Exception {
        when(userService.get(9L)).thenThrow(new UserNotFoundException(9L));

        mockMvc.perform(get("/api/v1/users/me").with(tokenFor(9L, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User 9 not found"));
    }

    @Test
    void meWithoutTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/me"));

        verifyNoInteractions(userService);
    }

    @Test
    void invalidBearerTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer error=\"invalid_token\"")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired"));

        verifyNoInteractions(userService);
    }

    @Test
    void adminCanListUsers() throws Exception {
        when(userService.list(1, 5))
                .thenReturn(new PageResponse<>(
                        List.of(new UserResponse(1L, "admin@example.com", Role.ADMIN, NOW)), 1, 5, 6, 2));

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "1")
                        .param("size", "5")
                        .with(tokenFor(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void userCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(tokenFor(7L, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users"));

        verifyNoInteractions(userService);
    }

    @Test
    void methodSecurityDenialInsideServiceReturns403NotGeneric500() throws Exception {
        when(userService.list(0, 20)).thenThrow(new AuthorizationDeniedException("Access Denied"));

        mockMvc.perform(get("/api/v1/users").with(tokenFor(1L, "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource"));
    }

    @Test
    void listRejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("size", "101").with(tokenFor(1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));

        verifyNoInteractions(userService);
    }
}
