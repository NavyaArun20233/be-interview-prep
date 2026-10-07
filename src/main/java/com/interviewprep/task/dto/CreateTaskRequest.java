package com.interviewprep.task.dto;

import com.interviewprep.task.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request body for creating a task; {@code status} defaults to {@link TaskStatus#TODO} when omitted. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        TaskStatus status,
        @FutureOrPresent LocalDate dueDate) {}
