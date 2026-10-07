package com.interviewprep.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is an absolute {@code http} or {@code https} URL with a host. {@code null} and blank values
 * are valid so that {@code @NotBlank} alone reports them.
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    String message() default "must be a valid http or https URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
