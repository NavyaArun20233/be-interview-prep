package com.interviewprep.task;

import com.interviewprep.task.dto.CreateTaskRequest;
import com.interviewprep.task.dto.PageResponse;
import com.interviewprep.task.dto.TaskResponse;
import com.interviewprep.task.dto.UpdateTaskRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        TaskStatus status = Optional.ofNullable(request.status()).orElse(TaskStatus.TODO);
        Task task = new Task(request.title(), request.description(), status, request.dueDate(), now());
        Task saved = taskRepository.save(task);
        log.info("Created task {}", saved.getId());
        return TaskResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(long id) {
        return TaskResponse.from(findTask(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Optional<TaskStatus> status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SORT);
        Page<Task> tasks = status.map(s -> taskRepository.findByStatus(s, pageRequest))
                .orElseGet(() -> taskRepository.findAll(pageRequest));
        return PageResponse.from(tasks, TaskResponse::from);
    }

    @Transactional
    public TaskResponse update(long id, UpdateTaskRequest request) {
        Task task = findTask(id);
        task.update(request.title(), request.description(), request.status(), request.dueDate(), now());
        // Flush so the optimistic-lock version is incremented and checked before we build the response.
        Task saved = taskRepository.saveAndFlush(task);
        log.info("Updated task {}", id);
        return TaskResponse.from(saved);
    }

    @Transactional
    public void delete(long id) {
        Task task = findTask(id);
        taskRepository.delete(task);
        log.info("Deleted task {}", id);
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Task findTask(long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}
