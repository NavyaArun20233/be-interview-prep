package com.interviewprep.config;

import com.interviewprep.validation.Password;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Optional first administrator ({@code app.security.admin.*}, env {@code APP_SECURITY_ADMIN_EMAIL} and
 * {@code APP_SECURITY_ADMIN_PASSWORD}). There are no defaults: when neither is set, no admin is created.
 */
@Validated
@ConfigurationProperties("app.security.admin")
public record AdminBootstrapProperties(
        @Email String email, @Password String password) {

    @AssertTrue(message = "app.security.admin.email and app.security.admin.password must be set together")
    public boolean isEmailAndPasswordSetTogether() {
        return StringUtils.hasText(email) == StringUtils.hasText(password);
    }

    public boolean configured() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[email=" + email + ", password=***]";
    }
}
