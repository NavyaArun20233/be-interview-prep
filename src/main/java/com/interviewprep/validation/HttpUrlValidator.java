package com.interviewprep.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Syntax-only check (the URL is never fetched): parses with {@link URI}, which rejects whitespace and other illegal
 * characters, then requires an {@code http}/{@code https} scheme (any case) and a server-based host. This rules out
 * {@code javascript:}, {@code data:}, {@code ftp:}, relative URLs and URLs without a host.
 */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return httpScheme && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
