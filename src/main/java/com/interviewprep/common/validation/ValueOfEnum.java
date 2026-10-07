package com.interviewprep.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is the exact name of a constant of {@link #enumClass()}; {@code null} is valid.
 *
 * <p>Binding enums as strings lets an invalid value be reported together with the other field errors instead of
 * failing JSON deserialization on its own.
 */
@Documented
@Constraint(validatedBy = ValueOfEnumValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueOfEnum {

    Class<? extends Enum<?>> enumClass();

    String message() default "must be one of: {allowedValues}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
