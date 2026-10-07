package com.interviewprep.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.config.ValidationConfig;
import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.task.CreateTaskRequest;
import com.interviewprep.dto.task.TaskResponse;
import com.interviewprep.dto.task.UpdateTaskRequest;
import com.interviewprep.entity.TaskStatus;
import com.interviewprep.exception.TaskNotFoundException;
import com.interviewprep.service.TaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@Import({ValidationConfig.class, TaskControllerTest.FixedClockConfig.class})
class TaskControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    private static TaskResponse task(long id, String title, TaskStatus status, LocalDate dueDate) {
        return new TaskResponse(id, title, null, status, dueDate, NOW, NOW);
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class)))
                .thenReturn(task(1L, "Write code", TaskStatus.TODO, TODAY));

        // Due date equal to "today" of the injected clock is accepted (@FutureOrPresent uses that clock).
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Write code", "dueDate": "2026-01-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Write code"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value("2026-01-15"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));

        verify(taskService).create(new CreateTaskRequest("Write code", null, null, TODAY));
    }

    @Test
    void createRejectsBlankTitleAndPastDueDateWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "  ", "dueDate": "2026-01-14"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'dueDate')].message")
                        .value("must be a date in the present or in the future"));

        verifyNoInteractions(taskService);
    }

    @Test
    void createRejectsMissingTitle() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void createRejectsTitleLongerThan100Characters() throws Exception {
        String title = "a".repeat(101);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 100"));
    }

    @Test
    void createRejectsUnknownStatusListingAllowedValues() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task", "status": "FINISHED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of: TODO, IN_PROGRESS, DONE"));
    }

    @Test
    void createReportsUnknownStatusTogetherWithOtherFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "status": "DOING", "dueDate": "%s"}
                                """.formatted(TODAY.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'status')].message")
                        .value("must be one of: TODO, IN_PROGRESS, DONE"))
                .andExpect(jsonPath("$.errors[?(@.field == 'dueDate')].message")
                        .value("must be a date in the present or in the future"));
        verifyNoInteractions(taskService);
    }

    @Test
    void updateRejectsUnknownStatus() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task", "status": "FINISHED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of: TODO, IN_PROGRESS, DONE"));
        verifyNoInteractions(taskService);
    }

    @Test
    void createRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task", "dueDate": "15/01/2026"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid date in the format yyyy-MM-dd"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void getUnknownTaskReturns404ProblemDetail() throws Exception {
        when(taskService.get(42L)).thenThrow(new TaskNotFoundException(42L));

        mockMvc.perform(get("/api/v1/tasks/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task 42 not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks/42"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void getWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid long"));
    }

    @Test
    void listPassesStatusFilterAndPaging() throws Exception {
        when(taskService.list(eq(Optional.of(TaskStatus.IN_PROGRESS)), eq(1), eq(5)))
                .thenReturn(new PageResponse<>(List.of(task(3L, "Doing", TaskStatus.IN_PROGRESS, null)), 1, 5, 6, 2));

        mockMvc.perform(get("/api/v1/tasks")
                        .param("status", "IN_PROGRESS")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listUsesDefaultPagingWithoutFilter() throws Exception {
        when(taskService.list(Optional.empty(), 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));

        verify(taskService).list(Optional.empty(), 0, 20);
    }

    @Test
    void listRejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "DOING"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of: TODO, IN_PROGRESS, DONE"));

        verifyNoInteractions(taskService);
    }

    @Test
    void listRejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));

        verifyNoInteractions(taskService);
    }

    @Test
    void updateReturnsUpdatedTask() throws Exception {
        when(taskService.update(eq(5L), any(UpdateTaskRequest.class)))
                .thenReturn(task(5L, "Renamed", TaskStatus.DONE, null));

        mockMvc.perform(put("/api/v1/tasks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Renamed", "status": "DONE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void updateRequiresStatusAndRejectsPastDueDate() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Renamed", "dueDate": "2020-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'status')].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[?(@.field == 'dueDate')]").exists());

        verifyNoInteractions(taskService);
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/9")).andExpect(status().isNoContent());

        verify(taskService).delete(9L);
    }

    @Test
    void unsupportedMethodKeeps405AsProblemDetail() throws Exception {
        mockMvc.perform(put("/api/v1/tasks"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unsupportedMediaTypeKeeps415AsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.TEXT_PLAIN).content("title"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void unexpectedErrorReturnsGeneric500WithoutInternals() throws Exception {
        when(taskService.get(1L)).thenThrow(new IllegalStateException("connection to db-host:5432 refused"));

        mockMvc.perform(get("/api/v1/tasks/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
