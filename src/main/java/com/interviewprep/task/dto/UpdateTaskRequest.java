package com.interviewprep.task.dto;

import com.interviewprep.task.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request body for a full replacement (PUT) of a task; omitted optional fields are cleared. */
public record UpdateTaskRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        @NotNull TaskStatus status,
        @FutureOrPresent LocalDate dueDate) {}
