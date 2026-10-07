package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.config.CacheConfig;
import com.interviewprep.dto.order.OrderResponse;
import com.interviewprep.dto.product.ProductResponse;
import com.interviewprep.entity.OrderStatus;
import com.interviewprep.entity.Role;
import com.interviewprep.service.TokenService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Full stack against PostgreSQL. Each test uses its own users and products (category {@code TEST}), removed after. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderApiIT {

    static final String TEST_CATEGORY = "TEST";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheManager cacheManager;

    private long userId;
    private RestTestClient user;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
        userId = insertUser(jdbcTemplate, Role.USER);
        user = client(
                port, tokenService.issue(userId, "user@example.com", Role.USER).accessToken());
    }

    @AfterEach
    void cleanUp() {
        cleanUpTestData(jdbcTemplate);
    }

    @Test
    void placesOrderAndRetryWithSameKeyReturnsTheSameOrder() {
        long productId = insertProduct(jdbcTemplate, 10);
        String body = orderBody(productId, 3);

        EntityExchangeResult<OrderResponse> first = place(user, "key-1", body);
        assertThat(first.getStatus().value()).isEqualTo(201);
        OrderResponse order = first.getResponseBody();
        assertThat(first.getResponseHeaders().getLocation()).hasPath("/api/v1/orders/" + order.id());
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.totalAmount()).isEqualByComparingTo("29.97");
        assertThat(order.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(productId);
            assertThat(item.productName()).isEqualTo("Test product");
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.unitPrice()).isEqualByComparingTo("9.99");
        });

        EntityExchangeResult<OrderResponse> retry = place(user, "key-1", body);
        assertThat(retry.getStatus().value()).isEqualTo(200);
        assertThat(retry.getResponseHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getResponseBody()).isEqualTo(order);

        assertThat(orderCount()).isEqualTo(1);
        assertThat(stock(productId)).isEqualTo(7);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        long productId = insertProduct(jdbcTemplate, 10);
        assertThat(place(user, "key-1", orderBody(productId, 1)).getStatus().value())
                .isEqualTo(201);

        user.post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody(productId, 2))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Idempotency-Key was already used with a different request");

        assertThat(orderCount()).isEqualTo(1);
        assertThat(stock(productId)).isEqualTo(9);
    }

    @Test
    void orderIsAllOrNothingWhenOneItemIsShort() {
        long plenty = insertProduct(jdbcTemplate, 10);
        long scarce = insertProduct(jdbcTemplate, 1);

        user.post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"items\": [{\"productId\": " + plenty + ", \"quantity\": 2}, {\"productId\": " + scarce
                        + ", \"quantity\": 3}]}")
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Insufficient stock for product " + scarce + ": requested 3, available 1");

        assertThat(stock(plenty)).isEqualTo(10);
        assertThat(stock(scarce)).isEqualTo(1);
        assertThat(orderCount()).isZero();

        // The failed attempt rolled back, so the same key can be retried once the request can succeed.
        assertThat(place(user, "key-1", orderBody(plenty, 2)).getStatus().value())
                .isEqualTo(201);
    }

    @Test
    void unknownProductIsNotFound() {
        user.post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody(Long.MAX_VALUE, 1))
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(orderCount()).isZero();
    }

    @Test
    void cancelRestoresStockOnlyOnce() {
        long productId = insertProduct(jdbcTemplate, 10);
        OrderResponse order = place(user, "key-1", orderBody(productId, 4)).getResponseBody();
        assertThat(stock(productId)).isEqualTo(6);

        for (int attempt = 0; attempt < 2; attempt++) {
            OrderResponse cancelled = user.post()
                    .uri("/api/v1/orders/{id}/cancel", order.id())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .returnResult(OrderResponse.class)
                    .getResponseBody();
            assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(stock(productId)).isEqualTo(10);
        }
    }

    @Test
    void ordersOfOtherUsersAreHiddenExceptFromAdmins() {
        long productId = insertProduct(jdbcTemplate, 10);
        OrderResponse order = place(user, "key-1", orderBody(productId, 1)).getResponseBody();
        long otherId = insertUser(jdbcTemplate, Role.USER);
        RestTestClient other = client(
                port,
                tokenService.issue(otherId, "other@example.com", Role.USER).accessToken());

        other.get()
                .uri("/api/v1/orders/{id}", order.id())
                .exchange()
                .expectStatus()
                .isNotFound();
        other.post()
                .uri("/api/v1/orders/{id}/cancel", order.id())
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(stock(productId)).isEqualTo(9);

        long adminId = insertUser(jdbcTemplate, Role.ADMIN);
        RestTestClient admin = client(
                port,
                tokenService.issue(adminId, "admin@example.com", Role.ADMIN).accessToken());
        OrderResponse seenByAdmin = admin.get()
                .uri("/api/v1/orders/{id}", order.id())
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(OrderResponse.class)
                .getResponseBody();
        assertThat(seenByAdmin).isEqualTo(order);
    }

    @Test
    void rejectsMissingOrInvalidKeyAndMissingToken() {
        long productId = insertProduct(jdbcTemplate, 10);

        user.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody(productId, 1))
                .exchange()
                .expectStatus()
                .isBadRequest();
        user.post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, "not valid!")
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody(productId, 1))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("idempotencyKey");
        user.post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"items\": [{\"productId\": " + productId + ", \"quantity\": 0}]}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("items[0].quantity");
        RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderBody(productId, 1))
                .exchange()
                .expectStatus()
                .isUnauthorized();

        assertThat(orderCount()).isZero();
        assertThat(stock(productId)).isEqualTo(10);
    }

    @Test
    void cachedProductReflectsStockChangesAndOrderedProductCannotBeDeleted() {
        long productId = insertProduct(jdbcTemplate, 10);
        assertThat(getProduct(productId).stock()).isEqualTo(10); // now cached

        OrderResponse order = place(user, "key-1", orderBody(productId, 3)).getResponseBody();
        assertThat(getProduct(productId).stock()).isEqualTo(7);

        user.post()
                .uri("/api/v1/orders/{id}/cancel", order.id())
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(getProduct(productId).stock()).isEqualTo(10);

        long adminId = insertUser(jdbcTemplate, Role.ADMIN);
        client(
                        port,
                        tokenService
                                .issue(adminId, "admin@example.com", Role.ADMIN)
                                .accessToken())
                .delete()
                .uri("/api/v1/products/{id}", productId)
                .exchange()
                .expectStatus()
                .isEqualTo(409);
    }

    private ProductResponse getProduct(long id) {
        return user.get()
                .uri("/api/v1/products/{id}", id)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ProductResponse.class)
                .getResponseBody();
    }

    private int orderCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, userId);
    }

    private int stock(long productId) {
        return stockOf(jdbcTemplate, productId);
    }

    static EntityExchangeResult<OrderResponse> place(RestTestClient client, String key, String body) {
        return client.post()
                .uri("/api/v1/orders")
                .header(IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .returnResult(OrderResponse.class);
    }

    static String orderBody(long productId, int quantity) {
        return "{\"items\": [{\"productId\": " + productId + ", \"quantity\": " + quantity + "}]}";
    }

    static long insertUser(JdbcTemplate jdbcTemplate, Role role) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, password_hash, role, created_at, updated_at)
                VALUES (?, '{noop}unused', ?, now(), now())
                RETURNING id
                """, Long.class, "order-it-" + UUID.randomUUID() + "@example.com", role.name());
    }

    static long insertProduct(JdbcTemplate jdbcTemplate, int stock) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO products (name, category, price, stock, rating, created_at, updated_at)
                VALUES ('Test product', ?, 9.99, ?, 3.5, now(), now())
                RETURNING id
                """, Long.class, TEST_CATEGORY, stock);
    }

    static int stockOf(JdbcTemplate jdbcTemplate, long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    /** Orders first (their items reference the test products), then the test products and users. */
    static void cleanUpTestData(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'order-it-%')");
        jdbcTemplate.update("DELETE FROM products WHERE category = ?", TEST_CATEGORY);
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'order-it-%'");
    }

    static RestTestClient client(int port, String token) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }
}
