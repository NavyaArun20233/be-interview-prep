package com.interviewprep.dto.auth;

import com.interviewprep.entity.Role;
import com.interviewprep.entity.User;
import java.time.Instant;

/** Public profile of a user; never includes the password hash. */
public record UserResponse(Long id, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
