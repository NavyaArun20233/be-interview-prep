package com.interviewprep.exception;

public class EmailAlreadyRegisteredException extends ResourceConflictException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists");
    }
}
