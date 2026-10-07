package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestSecrets;
import com.interviewprep.config.JwtConfig;
import com.interviewprep.config.JwtProperties;
import com.interviewprep.dto.auth.TokenResponse;
import com.interviewprep.entity.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class TokenServiceTest {

    // Not on a whole second: JWT times are whole seconds, so issue time is truncated.
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00.750Z");
    private static final Instant ISSUED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final JwtProperties properties = new JwtProperties(TestSecrets.randomJwtSecret(), Duration.ofMinutes(15));
    private final JwtConfig jwtConfig = new JwtConfig();
    private final TokenService tokenService = new TokenService(jwtConfig.jwtEncoder(properties), properties, CLOCK);
    private final JwtDecoder decoder = jwtConfig.jwtDecoder(properties, CLOCK);

    @Test
    void issuesSignedTokenWithIdentityRolesAndFifteenMinuteExpiry() {
        TokenResponse response = tokenService.issue(7L, "ada@example.com", Role.USER);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(15)));

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ada@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("be-interview-prep");
        assertThat(jwt.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(jwt.getExpiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(15)));
    }

    @Test
    void adminTokenCarriesAdminRole() {
        Jwt jwt = decoder.decode(
                tokenService.issue(1L, "admin@example.com", Role.ADMIN).accessToken());

        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void responseDoesNotPrintTheToken() {
        TokenResponse response = tokenService.issue(7L, "ada@example.com", Role.USER);

        assertThat(response.toString()).doesNotContain(response.accessToken());
    }
}
