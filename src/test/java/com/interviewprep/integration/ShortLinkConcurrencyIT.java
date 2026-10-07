package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.link.ShortLinkResponse;
import com.interviewprep.dto.link.ShortLinkStatsResponse;
import com.interviewprep.service.TokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Many clients hitting the same link at the same moment, through HTTP and real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ShortLinkConcurrencyIT {

    private static final int CLIENTS = 100;
    private static final String URL = "https://example.com/popular";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM short_links");
        client = ShortLinkApiIT.noRedirectClient(port, ShortLinkApiIT.userToken(tokenService));
    }

    @Test
    void concurrentVisitsAreAllCounted() throws Exception {
        ShortLinkResponse link = shorten();

        List<Integer> statuses = runConcurrently(() -> client.get()
                .uri("/{code}", link.code())
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value());

        assertThat(statuses).hasSize(CLIENTS).containsOnly(302);
        ShortLinkStatsResponse stats = client.get()
                .uri("/api/v1/links/{code}/stats", link.code())
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ShortLinkStatsResponse.class)
                .getResponseBody();
        assertThat(stats.visitCount()).isEqualTo(CLIENTS);
    }

    @Test
    void concurrentIdenticalShortenRequestsShareOneCode() throws Exception {
        List<EntityExchangeResult<ShortLinkResponse>> results = runConcurrently(() -> client.post()
                .uri("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"" + URL + "\"}")
                .exchange()
                .returnResult(ShortLinkResponse.class));

        assertThat(results)
                .extracting(result -> result.getStatus().value())
                .containsOnly(200, 201)
                .filteredOn(status -> status == 201)
                .hasSize(1);
        assertThat(results)
                .extracting(result -> result.getResponseBody().code())
                .containsOnly(results.getFirst().getResponseBody().code());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM short_links", Integer.class))
                .isEqualTo(1);
    }

    private ShortLinkResponse shorten() {
        return client.post()
                .uri("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"" + URL + "\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(ShortLinkResponse.class)
                .getResponseBody();
    }

    /** Runs {@link #CLIENTS} copies of the task, released together by a start gate to maximize contention. */
    private <T> List<T> runConcurrently(Callable<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CLIENTS);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < CLIENTS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
