package com.interviewprep.dto.auth;

import com.interviewprep.validation.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration. There is deliberately no {@code role} component: every self-registered account is a USER, and an
 * unknown {@code role} property in the JSON body is ignored.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Password String password) {

    public RegisterRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
