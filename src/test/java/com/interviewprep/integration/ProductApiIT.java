package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.config.CacheConfig;
import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.product.ProductResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.repository.ProductRepository;
import com.interviewprep.service.TokenService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Full stack against PostgreSQL with the 100 products seeded by Flyway (V4). Tests only read the seed data; tests that
 * write use their own product (category {@code TEST}), removed after each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductApiIT {

    private static final String TEST_CATEGORY = "TEST";
    private static final ParameterizedTypeReference<PageResponse<ProductResponse>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    @LocalServerPort
    private int port;

    /** Spy on the real repository: proves whether a request reached the database. */
    @MockitoSpyBean
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    private RestTestClient user;
    private RestTestClient admin;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
        user = client(tokenService.issue(1L, "user@example.com", Role.USER).accessToken());
        admin = client(tokenService.issue(2L, "admin@example.com", Role.ADMIN).accessToken());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM products WHERE category = ?", TEST_CATEGORY);
    }

    @Test
    void seedsOneHundredProducts() {
        PageResponse<ProductResponse> page = list("/api/v1/products");

        assertThat(page.totalElements()).isEqualTo(100);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalPages()).isEqualTo(5);
        assertThat(page.content()).hasSize(20);
    }

    @Test
    void combinesAllFiltersInOneRequest() {
        PageResponse<ProductResponse> page = list("/api/v1/products?category=ELECTRONICS&minPrice=100&maxPrice=400"
                + "&inStock=true&q=PRODUCT 0&size=5&sort=price,asc");

        assertThat(page.totalElements()).isPositive();
        assertThat(page.totalPages()).isEqualTo((int) Math.ceil(page.totalElements() / 5.0));
        assertThat(page.content()).isNotEmpty().allSatisfy(product -> {
            assertThat(product.category()).isEqualTo("ELECTRONICS");
            assertThat(product.price()).isBetween(new BigDecimal("100"), new BigDecimal("400"));
            assertThat(product.stock()).isPositive();
            assertThat(product.name()).startsWith("Product 0");
        });
        assertThat(page.content()).isSortedAccordingTo(Comparator.comparing(ProductResponse::price));
    }

    @Test
    void sortsByAnyWhitelistedFieldAndCapsPageSize() {
        List<ProductResponse> byPriceDesc =
                list("/api/v1/products?sort=price,desc&size=500").content();
        assertThat(byPriceDesc).hasSize(100);
        assertThat(byPriceDesc)
                .isSortedAccordingTo(Comparator.comparing(ProductResponse::price, Comparator.reverseOrder()));

        PageResponse<ProductResponse> capped = list("/api/v1/products?size=500");
        assertThat(capped.size()).isEqualTo(100);

        user.get()
                .uri("/api/v1/products?sort=secret,asc")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("sort");

        user.get()
                .uri("/api/v1/products?minPrice=50&maxPrice=10")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("minPrice");
    }

    @Test
    void repeatedLookupsHitTheCacheAndWritesNeverLeaveItStale() {
        long id = insertTestProduct();

        ProductResponse first = get(id);
        ProductResponse second = get(id);
        assertThat(second).isEqualTo(first);
        // Two GETs, one database read: the second was served from the cache.
        verify(productRepository, times(1)).findById(id);

        admin.put()
                .uri("/api/v1/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "Renamed", "category": "TEST", "price": 12.50, "stock": 0, "rating": 4.5}
                        """)
                .exchange()
                .expectStatus()
                .isOk();

        ProductResponse afterUpdate = get(id);
        assertThat(afterUpdate.name()).isEqualTo("Renamed");
        assertThat(afterUpdate.price()).isEqualByComparingTo("12.50");
        assertThat(afterUpdate.stock()).isZero();

        admin.delete()
                .uri("/api/v1/products/{id}", id)
                .exchange()
                .expectStatus()
                .isNoContent();

        user.get().uri("/api/v1/products/{id}", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void writesRequireAdminRole() {
        long id = insertTestProduct();

        user.put()
                .uri("/api/v1/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "Hacked", "category": "TEST", "price": 1, "stock": 1, "rating": 1}
                        """)
                .exchange()
                .expectStatus()
                .isForbidden();
        user.delete().uri("/api/v1/products/{id}", id).exchange().expectStatus().isForbidden();

        assertThat(get(id).name()).isEqualTo("Test product");
    }

    private long insertTestProduct() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO products (name, category, price, stock, rating, created_at, updated_at)
                VALUES ('Test product', ?, 9.99, 5, 3.5, now(), now())
                RETURNING id
                """, Long.class, TEST_CATEGORY);
    }

    private ProductResponse get(long id) {
        return user.get()
                .uri("/api/v1/products/{id}", id)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ProductResponse.class)
                .getResponseBody();
    }

    private PageResponse<ProductResponse> list(String uri) {
        return user.get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(PAGE_TYPE)
                .getResponseBody();
    }

    private RestTestClient client(String token) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }
}
