package com.interviewprep.exception;

public class TaskNotFoundException extends ResourceNotFoundException {

    public TaskNotFoundException(long id) {
        super("Task " + id + " not found");
    }
}
