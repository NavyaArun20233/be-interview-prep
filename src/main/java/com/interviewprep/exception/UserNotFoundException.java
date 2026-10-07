package com.interviewprep.exception;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(long id) {
        super("User " + id + " not found");
    }
}
