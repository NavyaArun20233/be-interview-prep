package com.interviewprep.common.error;

/** Base type for "resource does not exist" errors; mapped to 404 by {@link GlobalExceptionHandler}. */
public abstract class ResourceNotFoundException extends RuntimeException {

    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
