package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.task.TaskResponse;
import com.interviewprep.entity.TaskStatus;
import com.interviewprep.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Full stack: HTTP -> controller -> service -> JPA -> PostgreSQL schema created by Flyway (ddl-auto=validate). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TaskApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void flywayMigrationIsAppliedAndSchemaValidates() {
        // The context only starts if Hibernate's ddl-auto=validate accepts the Flyway-created schema.
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("1");
    }

    @Test
    void crudRoundTrip() {
        LocalDate dueDate = LocalDate.now(clock).plusDays(7);

        TaskResponse created = createTask("""
                {"title": "Prepare interview", "description": "Read the docs", "dueDate": "%s"}
                """.formatted(dueDate));
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(created.dueDate()).isEqualTo(dueDate);
        assertThat(created.createdAt()).isNotNull().isEqualTo(created.updatedAt());

        client.get()
                .uri("/api/v1/tasks/{id}", created.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Prepare interview")
                .jsonPath("$.description")
                .isEqualTo("Read the docs");

        TaskResponse updated = client.put()
                .uri("/api/v1/tasks/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Prepare interview v2", "status": "IN_PROGRESS"}
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(TaskResponse.class)
                .getResponseBody();
        assertThat(updated.title()).isEqualTo("Prepare interview v2");
        assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.description()).isNull();
        assertThat(updated.dueDate()).isNull();
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());

        client.delete()
                .uri("/api/v1/tasks/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();

        client.get()
                .uri("/api/v1/tasks/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void listFiltersByStatusAndPaginates() {
        createTask("{\"title\": \"A\", \"status\": \"TODO\"}");
        createTask("{\"title\": \"B\", \"status\": \"DONE\"}");
        createTask("{\"title\": \"C\", \"status\": \"DONE\"}");

        client.get()
                .uri("/api/v1/tasks?status=DONE&size=1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].status")
                .isEqualTo("DONE")
                .jsonPath("$.page")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(1)
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.totalPages")
                .isEqualTo(2);

        client.get()
                .uri("/api/v1/tasks")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(3);
    }

    @Test
    void unknownTaskReturns404ProblemDetail() {
        client.get()
                .uri("/api/v1/tasks/999999")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("about:blank")
                .jsonPath("$.title")
                .isEqualTo("Not Found")
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.detail")
                .isEqualTo("Task 999999 not found")
                .jsonPath("$.instance")
                .isEqualTo("/api/v1/tasks/999999");
    }

    @Test
    void invalidInputReturns400WithFieldErrors() {
        String pastDate = LocalDate.now(clock).minusDays(1).toString();

        client.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\": \"\", \"dueDate\": \"" + pastDate + "\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.detail")
                .isEqualTo("Validation failed")
                .jsonPath("$.errors.length()")
                .isEqualTo(2)
                .jsonPath("$.errors[?(@.field == 'title')].message")
                .isEqualTo("must not be blank")
                .jsonPath("$.errors[?(@.field == 'dueDate')].message")
                .isEqualTo("must be a date in the present or in the future");

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void unknownPathReturns404ProblemDetailInsteadOfWhitelabelPage() {
        client.get()
                .uri("/api/v1/does-not-exist")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404);
    }

    private TaskResponse createTask(String json) {
        return client.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists("Location")
                .returnResult(TaskResponse.class)
                .getResponseBody();
    }
}
