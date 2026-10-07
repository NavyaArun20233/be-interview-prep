package com.interviewprep.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<Password, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        int characters = value.codePointCount(0, value.length());
        return characters >= Password.MIN_LENGTH && fitsBcrypt(value);
    }

    /** bcrypt only uses the first 72 bytes of a password; longer input is rejected rather than silently truncated. */
    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= Password.MAX_BYTES;
    }
}
