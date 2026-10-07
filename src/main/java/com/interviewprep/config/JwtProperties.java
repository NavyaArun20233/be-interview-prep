package com.interviewprep.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Access-token settings ({@code app.security.jwt.*}). Validated at startup so the application fails fast instead of
 * signing tokens with a missing or weak key.
 *
 * @param secret HMAC-SHA256 signing key, at least 32 bytes (256 bits) in UTF-8. Required and without a default: set
 *     environment variable {@code APP_SECURITY_JWT_SECRET} (e.g. {@code openssl rand -base64 48}).
 * @param ttl access-token lifetime, default 15 minutes
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank(message = "must be set (environment variable APP_SECURITY_JWT_SECRET)")
        String secret,

        @DefaultValue("PT15M") @NotNull Duration ttl) {

    /** HS256 needs a key at least as long as its 256-bit output (RFC 7518, section 3.2). */
    public static final int MIN_SECRET_BYTES = 32;

    /** A missing secret is left to {@code @NotBlank}, so only one error is reported. */
    @AssertTrue(
            message = "app.security.jwt.secret must be at least 32 bytes (256 bits);"
                    + " generate one with: openssl rand -base64 48")
    public boolean isSecretLongEnough() {
        return secret == null || secret.isBlank() || secretBytes().length >= MIN_SECRET_BYTES;
    }

    @AssertTrue(message = "app.security.jwt.ttl must be positive")
    public boolean isTtlPositive() {
        return ttl == null || ttl.isPositive();
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(secretBytes(), "HmacSHA256");
    }

    private byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, ttl=" + ttl + "]";
    }
}
