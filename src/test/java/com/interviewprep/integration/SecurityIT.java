package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestSecrets;
import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.config.JwtConfig;
import com.interviewprep.config.JwtProperties;
import com.interviewprep.dto.auth.TokenResponse;
import com.interviewprep.dto.auth.UserResponse;
import com.interviewprep.dto.link.ShortLinkResponse;
import com.interviewprep.entity.Role;
import com.interviewprep.service.TokenService;
import com.interviewprep.service.UserService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Authentication and authorization through HTTP against PostgreSQL: who may call what, and that 401/403 are Problem
 * Details JSON. The first ADMIN comes from the startup bootstrap, configured here with a password generated at runtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SecurityIT {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = TestSecrets.randomSecret(24);
    private static final String PASSWORD = "correct horse battery";

    @DynamicPropertySource
    static void adminBootstrap(DynamicPropertyRegistry registry) {
        registry.add("app.security.admin.email", () -> ADMIN_EMAIL);
        registry.add("app.security.admin.password", () -> ADMIN_PASSWORD);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    /** Sends no token unless a test adds one; does not follow redirects. */
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM users WHERE email <> ?", ADMIN_EMAIL);
        client = ShortLinkApiIT.noRedirectClient(port);
    }

    @Test
    void usersMigrationIsApplied() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("3");
    }

    @Test
    void requestsWithoutTokenReturn401ProblemDetailsJson() {
        for (String path :
                List.of("/api/v1/users/me", "/api/v1/users", "/api/v1/tasks", "/api/v1/links/abc1234/stats")) {
            client.get()
                    .uri(path)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectHeader()
                    .valueMatches(HttpHeaders.WWW_AUTHENTICATE, "^Bearer.*")
                    .expectBody()
                    .jsonPath("$.type")
                    .isEqualTo("about:blank")
                    .jsonPath("$.title")
                    .isEqualTo("Unauthorized")
                    .jsonPath("$.status")
                    .isEqualTo(401)
                    .jsonPath("$.detail")
                    .isEqualTo("Authentication is required to access this resource")
                    .jsonPath("$.instance")
                    .isEqualTo(path);
        }
    }

    /** Acceptance test: a logged-in USER is refused the admin-only endpoint with 403 Problem Details JSON. */
    @Test
    void userCannotListAllUsers() {
        register("ada@example.com", PASSWORD);
        String userToken = login("ada@example.com", PASSWORD).accessToken();

        client.get()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("about:blank")
                .jsonPath("$.title")
                .isEqualTo("Forbidden")
                .jsonPath("$.status")
                .isEqualTo(403)
                .jsonPath("$.detail")
                .isEqualTo("You do not have permission to access this resource")
                .jsonPath("$.instance")
                .isEqualTo("/api/v1/users");
    }

    @Test
    void bootstrapAdminCanListAllUsersWithoutPasswordHashes() {
        register("ada@example.com", PASSWORD);
        TokenResponse adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        byte[] body = client.get()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken.accessToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.content[?(@.email == 'admin@example.com')].role")
                .isEqualTo("ADMIN")
                .jsonPath("$.content[?(@.email == 'ada@example.com')].role")
                .isEqualTo("USER")
                .returnResult()
                .getResponseBodyContent();

        assertThat(new String(body, StandardCharsets.UTF_8))
                .doesNotContainIgnoringCase("password")
                .doesNotContain("{bcrypt}");
    }

    @Test
    void adminBootstrapIsIdempotent() {
        assertThat(userService.createAdminIfAbsent(ADMIN_EMAIL, ADMIN_PASSWORD)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE email = ? AND role = 'ADMIN'", Integer.class, ADMIN_EMAIL))
                .isEqualTo(1);
    }

    @Test
    void registerLoginAndViewOwnProfile() {
        UserResponse registered = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\": \"  Grace@Example.COM \", \"password\": \"" + PASSWORD + "\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches(HttpHeaders.LOCATION, "http://localhost:\\d+/api/v1/users/me")
                .returnResult(UserResponse.class)
                .getResponseBody();
        assertThat(registered.email()).isEqualTo("grace@example.com");
        assertThat(registered.role()).isEqualTo(Role.USER);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, registered.id());
        assertThat(storedHash).startsWith("{bcrypt}$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();

        // Login is case-insensitive on the email.
        TokenResponse token = login("GRACE@example.com", PASSWORD);
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(900);
        assertThat(token.expiresAt())
                .isAfter(clock.instant())
                .isBefore(clock.instant().plus(Duration.ofMinutes(16)));

        UserResponse me = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken())
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(UserResponse.class)
                .getResponseBody();
        assertThat(me).isEqualTo(registered);
    }

    @Test
    void selfRegistrationCannotChooseAdminRole() {
        UserResponse registered = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\": \"eve@example.com\", \"password\": \"" + PASSWORD + "\", \"role\": \"ADMIN\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(UserResponse.class)
                .getResponseBody();

        assertThat(registered.role()).isEqualTo(Role.USER);
        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, registered.id()))
                .isEqualTo("USER");
    }

    @Test
    void duplicateEmailInAnyCaseReturns409() {
        register("ada@example.com", PASSWORD);

        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\": \"ADA@example.com\", \"password\": \"another password\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("An account with this email already exists");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() {
        register("ada@example.com", PASSWORD);

        for (String body : List.of(
                "{\"email\": \"ada@example.com\", \"password\": \"wrong password\"}",
                "{\"email\": \"nobody@example.com\", \"password\": \"" + PASSWORD + "\"}")) {
            client.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.detail")
                    .isEqualTo("Invalid email or password");
        }
    }

    @Test
    void expiredTokenReturns401() {
        UserResponse user = register("ada@example.com", PASSWORD);
        // Issued 15 minutes and 1 second ago: expired one second ago, with no clock-skew allowance.
        TokenService pastTokenService =
                new TokenService(jwtEncoder, jwtProperties, Clock.offset(clock, Duration.ofSeconds(-901)));
        String expired =
                pastTokenService.issue(user.id(), user.email(), Role.USER).accessToken();

        expectInvalidToken("/api/v1/users/me", expired);
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() {
        UserResponse user = register("ada@example.com", PASSWORD);
        JwtProperties otherKey = new JwtProperties(TestSecrets.randomJwtSecret(), Duration.ofMinutes(15));
        String forged = new TokenService(new JwtConfig().jwtEncoder(otherKey), otherKey, clock)
                .issue(user.id(), user.email(), Role.ADMIN)
                .accessToken();

        expectInvalidToken("/api/v1/users", forged);
    }

    @Test
    void userCannotGrantThemselvesAdminByEditingTheToken() {
        register("ada@example.com", PASSWORD);
        String[] parts = login("ada@example.com", PASSWORD).accessToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"USER\"");
        String adminPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));

        expectInvalidToken("/api/v1/users", parts[0] + "." + adminPayload + "." + parts[2]);
    }

    @Test
    void shortLinkRedirectHealthAndTokenMetadataArePublic() {
        register("ada@example.com", PASSWORD);
        String token = login("ada@example.com", PASSWORD).accessToken();
        ShortLinkResponse link = client.post()
                .uri("/api/v1/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"url\": \"https://example.com/security-it\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(ShortLinkResponse.class)
                .getResponseBody();

        client.get()
                .uri("/{code}", link.code())
                .exchange()
                .expectStatus()
                .isFound()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "https://example.com/security-it");
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get()
                .uri("/.well-known/oauth-protected-resource")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.bearer_methods_supported[0]")
                .isEqualTo("header")
                .jsonPath("$.tls_client_certificate_bound_access_tokens")
                .isEqualTo(false);
        jdbcTemplate.update("DELETE FROM short_links WHERE code = ?", link.code());
    }

    private void expectInvalidToken(String path, String token) {
        client.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader()
                .valueMatches(HttpHeaders.WWW_AUTHENTICATE, "^Bearer error=\"invalid_token\".*")
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.detail")
                .isEqualTo("The access token is invalid or has expired");
    }

    private UserResponse register(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(UserResponse.class)
                .getResponseBody();
    }

    private TokenResponse login(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(TokenResponse.class)
                .getResponseBody();
    }
}
