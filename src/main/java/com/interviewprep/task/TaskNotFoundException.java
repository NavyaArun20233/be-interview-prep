package com.interviewprep.task;

import com.interviewprep.common.error.ResourceNotFoundException;

public class TaskNotFoundException extends ResourceNotFoundException {

    public TaskNotFoundException(long id) {
        super("Task " + id + " not found");
    }
}
