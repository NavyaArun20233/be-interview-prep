package com.interviewprep.controller;

import com.interviewprep.dto.auth.LoginRequest;
import com.interviewprep.dto.auth.RegisterRequest;
import com.interviewprep.dto.auth.TokenResponse;
import com.interviewprep.dto.auth.UserResponse;
import com.interviewprep.service.AuthService;
import com.interviewprep.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public endpoints (no token required): self-registration and login. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /** 201 with the new profile; {@code Location} is the caller's own profile, readable after logging in. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = userService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/users/me")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
