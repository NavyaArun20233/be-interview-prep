package com.interviewprep.dto.task;

import com.interviewprep.entity.TaskStatus;
import com.interviewprep.validation.ValueOfEnum;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request body for creating a task; {@code status} defaults to {@link TaskStatus#TODO} when omitted. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        @ValueOfEnum(enumClass = TaskStatus.class) String status,
        @FutureOrPresent LocalDate dueDate) {

    /** The validated {@code status}, or {@code null} when omitted. Call only after validation. */
    public TaskStatus statusValue() {
        return status == null ? null : TaskStatus.valueOf(status);
    }
}
