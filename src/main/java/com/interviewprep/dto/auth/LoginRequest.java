package com.interviewprep.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Credentials are only checked for presence; any mismatch is reported as invalid credentials. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
