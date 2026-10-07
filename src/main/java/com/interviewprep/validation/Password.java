package com.interviewprep.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password length rule: 8 to 72 characters (Unicode code points) and at most 72 bytes in UTF-8, because bcrypt only
 * uses the first 72 bytes and Spring Security rejects longer input. {@code null} and blank values are valid so that
 * {@code @NotBlank} alone reports them.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    int MIN_LENGTH = 8;
    int MAX_BYTES = 72;

    String message() default "must be between 8 and 72 characters (at most 72 bytes in UTF-8)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
