package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.link.ShortLinkResponse;
import com.interviewprep.dto.link.ShortLinkStatsResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.service.ShortCodeGenerator;
import com.interviewprep.service.TokenService;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Full stack against PostgreSQL: create, redirect, stats, idempotency and expiry, using the V2 schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ShortLinkApiIT {

    private static final String URL = "https://example.com/articles/how-to-prepare?ref=it";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private ShortCodeGenerator codeGenerator;

    @Autowired
    private TokenService tokenService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM short_links");
        client = noRedirectClient(port, userToken(tokenService));
    }

    /** Redirects must be observed, not followed. Sends no token. */
    static RestTestClient noRedirectClient(int port) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return RestTestClient.bindToServer(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /** Same, authenticated with {@code accessToken} on every request. */
    static RestTestClient noRedirectClient(int port, String accessToken) {
        return noRedirectClient(port)
                .mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    /** The link API needs a token of any role; it does not look the user up, so no account is required. */
    static String userToken(TokenService tokenService) {
        return tokenService.issue(1L, "link-it@example.com", Role.USER).accessToken();
    }

    @Test
    void shortLinksMigrationIsApplied() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("1", "2");
    }

    @Test
    void createRedirectAndStatsRoundTrip() {
        ShortLinkResponse created = client.post()
                .uri("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"  " + URL + " \"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches("Location", "http://localhost:\\d+/api/v1/links/[A-Za-z0-9]{7}/stats")
                .returnResult(ShortLinkResponse.class)
                .getResponseBody();

        assertThat(created.code()).matches("[A-Za-z0-9]{7}");
        assertThat(created.shortUrl()).isEqualTo("http://localhost:8080/" + created.code());
        assertThat(created.originalUrl()).isEqualTo(URL);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.expiresAt()).isNull();

        for (int i = 0; i < 2; i++) {
            client.get()
                    .uri("/{code}", created.code())
                    .exchange()
                    .expectStatus()
                    .isFound()
                    .expectHeader()
                    .valueEquals("Location", URL)
                    .expectHeader()
                    .valueEquals("Cache-Control", "no-store");
        }

        ShortLinkStatsResponse stats = stats(created.code());
        assertThat(stats.code()).isEqualTo(created.code());
        assertThat(stats.originalUrl()).isEqualTo(URL);
        assertThat(stats.visitCount()).isEqualTo(2);
        assertThat(stats.createdAt()).isEqualTo(created.createdAt());
        assertThat(stats.expired()).isFalse();
    }

    @Test
    void sameUrlAndExpiryTwiceReturnsSameCodeWith200() {
        String expiry = OffsetDateTime.now(clock)
                .plusDays(30)
                .truncatedTo(ChronoUnit.SECONDS)
                .withOffsetSameInstant(ZoneOffset.ofHoursMinutes(5, 30))
                .toString();
        String body = "{\"url\": \"" + URL + "\", \"expiresAt\": \"" + expiry + "\"}";
        ShortLinkResponse first = createExpectingStatus(body, 201);

        // Same instant written with a different offset is the same expiry.
        String sameInstantUtc = OffsetDateTime.parse(expiry)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toString();
        ShortLinkResponse second =
                createExpectingStatus("{\"url\": \"" + URL + "\", \"expiresAt\": \"" + sameInstantUtc + "\"}", 200);

        assertThat(second).isEqualTo(first);
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void sameUrlWithoutExpiryTwiceReturnsSameCode() {
        ShortLinkResponse first = createExpectingStatus("{\"url\": \"" + URL + "\"}", 201);
        ShortLinkResponse second = createExpectingStatus("{\"url\": \"" + URL + "\"}", 200);

        assertThat(second.code()).isEqualTo(first.code());
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void differentExpiryCreatesDifferentCode() {
        String expiry = OffsetDateTime.now(clock).plusDays(1).toString();
        ShortLinkResponse withoutExpiry = createExpectingStatus("{\"url\": \"" + URL + "\"}", 201);
        ShortLinkResponse withExpiry =
                createExpectingStatus("{\"url\": \"" + URL + "\", \"expiresAt\": \"" + expiry + "\"}", 201);

        assertThat(withExpiry.code()).isNotEqualTo(withoutExpiry.code());
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void expiredLinkReturns410AndIsNotCountedButStatsRemainAvailable() {
        String expiry = OffsetDateTime.now(clock).plusHours(1).toString();
        ShortLinkResponse created =
                createExpectingStatus("{\"url\": \"" + URL + "\", \"expiresAt\": \"" + expiry + "\"}", 201);
        client.get().uri("/{code}", created.code()).exchange().expectStatus().isFound();

        // Move the expiry into the past directly in the database instead of waiting for it.
        jdbcTemplate.update(
                "UPDATE short_links SET expires_at = now() - interval '1 minute' WHERE code = ?", created.code());

        client.get()
                .uri("/{code}", created.code())
                .exchange()
                .expectStatus()
                .isEqualTo(410)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(410)
                .jsonPath("$.detail")
                .isEqualTo("Short link " + created.code() + " has expired");

        ShortLinkStatsResponse stats = stats(created.code());
        assertThat(stats.visitCount()).isEqualTo(1);
        assertThat(stats.expired()).isTrue();
        assertThat(stats.expiresAt()).isBefore(Instant.now(clock));
    }

    @Test
    void codeCollisionIsRetriedWithFreshCodeInTheSameRequest() {
        ShortLinkResponse existing = createExpectingStatus("{\"url\": \"https://example.com/first\"}", 201);
        doReturn(existing.code(), "Fresh01").when(codeGenerator).generate();

        // The colliding insert must not abort the PostgreSQL transaction, so the retry can still succeed.
        ShortLinkResponse second = createExpectingStatus("{\"url\": \"https://example.com/second\"}", 201);

        assertThat(second.code()).isEqualTo("Fresh01");
        assertThat(second.originalUrl()).isEqualTo("https://example.com/second");
        assertThat(stats(existing.code()).originalUrl()).isEqualTo("https://example.com/first");
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void unknownCodeReturns404OnRedirectAndStats() {
        client.get()
                .uri("/zzzzzzz")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Short link zzzzzzz not found");

        client.get()
                .uri("/api/v1/links/zzzzzzz/stats")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void invalidUrlIsRejectedWithoutStoringAnything() {
        client.post()
                .uri("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"ftp://example.com/file\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("url");

        assertThat(countRows()).isZero();
    }

    @Test
    void redirectRouteDoesNotShadowActuatorOrApi() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/tasks").exchange().expectStatus().isOk();
    }

    private ShortLinkResponse createExpectingStatus(String body, int status) {
        return client.post()
                .uri("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(status)
                .returnResult(ShortLinkResponse.class)
                .getResponseBody();
    }

    private ShortLinkStatsResponse stats(String code) {
        return client.get()
                .uri("/api/v1/links/{code}/stats", code)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ShortLinkStatsResponse.class)
                .getResponseBody();
    }

    private int countRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM short_links", Integer.class);
    }
}
