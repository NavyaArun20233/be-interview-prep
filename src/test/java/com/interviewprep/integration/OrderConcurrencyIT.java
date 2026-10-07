package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.order.OrderResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.service.TokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
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

/** Many customers ordering the same product at the same moment, through HTTP and real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderConcurrencyIT {

    private static final int CLIENTS = 50;
    private static final int STOCK = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    private long userId;
    private long productId;
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        userId = OrderApiIT.insertUser(jdbcTemplate, Role.USER);
        productId = OrderApiIT.insertProduct(jdbcTemplate, 0);
        jdbcTemplate.update("UPDATE products SET stock = ? WHERE id = ?", STOCK, productId);
        client = OrderApiIT.client(
                port, tokenService.issue(userId, "user@example.com", Role.USER).accessToken());
    }

    @AfterEach
    void cleanUp() {
        OrderApiIT.cleanUpTestData(jdbcTemplate);
    }

    /** Acceptance: 50 simultaneous orders for a product with stock 10 - exactly 10 succeed, stock ends at 0. */
    @Test
    void simultaneousOrdersNeverOversell() throws Exception {
        // Status only: a 409 body is Problem Details, not an order.
        List<Integer> statuses = runConcurrently(i -> client.post()
                .uri("/api/v1/orders")
                .header(OrderApiIT.IDEMPOTENCY_KEY, "order-" + i)
                .contentType(MediaType.APPLICATION_JSON)
                .body(OrderApiIT.orderBody(productId, 1))
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(STOCK);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(CLIENTS - STOCK);
        assertThat(OrderApiIT.stockOf(jdbcTemplate, productId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(STOCK);
    }

    /** Acceptance: simultaneous retries of one request (same key) create exactly one order. */
    @Test
    void simultaneousRetriesCreateOneOrder() throws Exception {
        List<EntityExchangeResult<OrderResponse>> results =
                runConcurrently(i -> OrderApiIT.place(client, "same-key", OrderApiIT.orderBody(productId, 2)));

        assertThat(results)
                .extracting(result -> result.getStatus().value())
                .containsOnly(200, 201)
                .filteredOn(status -> status == 201)
                .hasSize(1);
        assertThat(results)
                .extracting(result -> result.getResponseBody().id())
                .containsOnly(results.getFirst().getResponseBody().id());
        assertThat(OrderApiIT.stockOf(jdbcTemplate, productId)).isEqualTo(STOCK - 2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    /** Runs {@link #CLIENTS} requests (given their index), released together by a start gate to maximize contention. */
    private <T> List<T> runConcurrently(IntFunction<T> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CLIENTS);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < CLIENTS; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return request.apply(index);
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
