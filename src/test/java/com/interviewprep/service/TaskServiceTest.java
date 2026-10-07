package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.task.CreateTaskRequest;
import com.interviewprep.dto.task.TaskResponse;
import com.interviewprep.dto.task.UpdateTaskRequest;
import com.interviewprep.entity.Task;
import com.interviewprep.entity.TaskStatus;
import com.interviewprep.exception.TaskNotFoundException;
import com.interviewprep.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TOMORROW = LocalDate.now(CLOCK).plusDays(1);

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, CLOCK);
    }

    @Test
    void createDefaultsStatusToTodoAndSetsTimestampsFromClock() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(new CreateTaskRequest("Write tests", "desc", null, TOMORROW));

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.title()).isEqualTo("Write tests");
        assertThat(response.dueDate()).isEqualTo(TOMORROW);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createKeepsExplicitStatus() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(new CreateTaskRequest("Ship it", null, "IN_PROGRESS", null));

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void getThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.get(42L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task 42 not found");
    }

    @Test
    void updateReplacesAllFieldsAndRefreshesUpdatedAt() {
        Instant created = NOW.minusSeconds(3600);
        Task existing = new Task("Old", "old desc", TaskStatus.TODO, TOMORROW, created);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(taskRepository.saveAndFlush(existing)).thenReturn(existing);

        TaskResponse response =
                taskService.update(7L, new UpdateTaskRequest("New", null, "DONE", TOMORROW.plusDays(1)));

        assertThat(response.title()).isEqualTo("New");
        assertThat(response.description()).isNull();
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        assertThat(response.dueDate()).isEqualTo(TOMORROW.plusDays(1));
        assertThat(response.createdAt()).isEqualTo(created);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(7L, new UpdateTaskRequest("New", null, "DONE", null)))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRemovesExistingTask() {
        Task existing = new Task("Delete me", null, TaskStatus.TODO, null, NOW);
        when(taskRepository.findById(3L)).thenReturn(Optional.of(existing));

        taskService.delete(3L);

        verify(taskRepository).delete(existing);
    }

    @Test
    void deleteThrowsWhenTaskDoesNotExist() {
        when(taskRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(3L)).isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void listFiltersByStatusWhenGiven() {
        Task task = new Task("Doing", null, TaskStatus.IN_PROGRESS, null, NOW);
        when(taskRepository.findByStatus(any(TaskStatus.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(task), invocation.getArgument(1), 1));

        PageResponse<TaskResponse> page = taskService.list(Optional.of(TaskStatus.IN_PROGRESS), 0, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findByStatus(eq(TaskStatus.IN_PROGRESS), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(page.content()).extracting(TaskResponse::title).containsExactly("Doing");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void listReturnsAllTasksWithoutFilter() {
        when(taskRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<TaskResponse> page = taskService.list(Optional.empty(), 0, 20);

        assertThat(page.content()).isEmpty();
        verify(taskRepository, never()).findByStatus(any(), any());
    }
}
