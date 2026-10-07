package com.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestSecrets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The application must refuse to start without a usable signing key. */
class JwtPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void missingSecretFailsStartupNamingTheEnvironmentVariable() {
        runner.run(context -> assertThat(context)
                .getFailure()
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("app.security.jwt")
                .hasMessageContaining("APP_SECURITY_JWT_SECRET"));
    }

    @Test
    void blankSecretFailsStartup() {
        runner.withPropertyValues("app.security.jwt.secret=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void secretShorterThan32BytesFailsStartupWithoutPrintingIt() {
        String shortSecret = TestSecrets.randomSecret(31);

        runner.withPropertyValues("app.security.jwt.secret=" + shortSecret)
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("at least 32 bytes")
                        .hasMessageNotContaining(shortSecret));
    }

    @Test
    void secretOf32BytesIsAcceptedAndTtlDefaultsToFifteenMinutes() {
        String secret = TestSecrets.randomSecret(32);

        runner.withPropertyValues("app.security.jwt.secret=" + secret).run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.toString()).doesNotContain(secret);
        });
    }

    @Test
    void nonPositiveTtlFailsStartup() {
        runner.withPropertyValues("app.security.jwt.secret=" + TestSecrets.randomJwtSecret(), "app.security.jwt.ttl=0s")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("app.security.jwt.ttl must be positive"));
    }
}
