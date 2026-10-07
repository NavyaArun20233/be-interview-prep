package com.interviewprep.exception;

/**
 * Login failed. Deliberately the same for an unknown email and a wrong password so that responses do not reveal which
 * accounts exist; mapped to 401 by {@link GlobalExceptionHandler}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
