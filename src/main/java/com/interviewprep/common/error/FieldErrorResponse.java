package com.interviewprep.common.error;

/** One entry of the {@code errors} array in a validation Problem Details response. */
public record FieldErrorResponse(String field, String message) {}
