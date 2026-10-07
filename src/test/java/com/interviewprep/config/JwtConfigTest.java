package com.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewprep.TestSecrets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Decoder rules: our key, our issuer, a mandatory {@code exp}, and no clock skew. */
class JwtConfigTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofMinutes(15));

    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtProperties properties = properties();
    private final JwtEncoder encoder = jwtConfig.jwtEncoder(properties);

    private static JwtProperties properties() {
        return new JwtProperties(TestSecrets.randomJwtSecret(), Duration.ofMinutes(15));
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject("7")
                .issuedAt(ISSUED_AT)
                .expiresAt(EXPIRES_AT)
                .claim(JwtConfig.ROLES_CLAIM, List.of("USER"));
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void tokenIsAcceptedUntilExactlyFifteenMinutesAndRejectedOneSecondLater() {
        String token = encode(encoder, claims().build());

        assertThat(jwtConfig
                        .jwtDecoder(properties, clockAt(ISSUED_AT))
                        .decode(token)
                        .getSubject())
                .isEqualTo("7");
        assertThat(jwtConfig
                        .jwtDecoder(properties, clockAt(EXPIRES_AT))
                        .decode(token)
                        .getSubject())
                .isEqualTo("7");
        // Spring's default 60 s clock skew would still accept this token.
        assertThatThrownBy(() -> jwtConfig
                        .jwtDecoder(properties, clockAt(EXPIRES_AT.plusSeconds(1)))
                        .decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = encode(encoder, claims().issuer("someone-else").build());

        assertThatThrownBy(() ->
                        jwtConfig.jwtDecoder(properties, clockAt(ISSUED_AT)).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenWithoutExpiryIsRejected() {
        JwtClaimsSet noExpiry = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject("7")
                .issuedAt(ISSUED_AT)
                .build();
        String token = encode(encoder, noExpiry);

        assertThatThrownBy(() ->
                        jwtConfig.jwtDecoder(properties, clockAt(ISSUED_AT)).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = encode(jwtConfig.jwtEncoder(properties()), claims().build());

        assertThatThrownBy(() ->
                        jwtConfig.jwtDecoder(properties, clockAt(ISSUED_AT)).decode(token))
                .isInstanceOf(BadJwtException.class);
    }
}
