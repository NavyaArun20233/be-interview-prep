package com.interviewprep.entity;

/** Authorization role of a {@link User}; carried in the access token as {@code roles: ["USER"]}. */
public enum Role {
    USER,
    ADMIN
}
