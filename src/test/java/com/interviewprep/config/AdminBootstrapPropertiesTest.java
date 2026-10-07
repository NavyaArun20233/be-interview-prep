package com.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminBootstrapPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(AdminBootstrapConfig.class);

    @Test
    void unsetIsValidAndNotConfigured() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AdminBootstrapProperties.class).configured())
                    .isFalse();
        });
    }

    @Test
    void emailAndPasswordTogetherAreConfiguredAndPasswordIsNotPrinted() {
        String password = TestSecrets.randomSecret(20);

        runner.withPropertyValues(
                        "app.security.admin.email=admin@example.com", "app.security.admin.password=" + password)
                .run(context -> {
                    AdminBootstrapProperties properties = context.getBean(AdminBootstrapProperties.class);
                    assertThat(properties.configured()).isTrue();
                    assertThat(properties.toString()).doesNotContain(password);
                });
    }

    @Test
    void emailWithoutPasswordFailsStartup() {
        runner.withPropertyValues("app.security.admin.email=admin@example.com")
                .run(context ->
                        assertThat(context).getFailure().rootCause().hasMessageContaining("must be set together"));
    }

    @Test
    void passwordBreakingTheRulesFailsStartup() {
        runner.withPropertyValues("app.security.admin.email=admin@example.com", "app.security.admin.password=short")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidEmailFailsStartup() {
        runner.withPropertyValues(
                        "app.security.admin.email=not-an-email",
                        "app.security.admin.password=" + TestSecrets.randomSecret(20))
                .run(context -> assertThat(context).hasFailed());
    }
}
