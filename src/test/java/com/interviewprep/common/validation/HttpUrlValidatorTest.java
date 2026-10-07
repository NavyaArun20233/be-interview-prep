package com.interviewprep.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlValidatorTest {

    private final HttpUrlValidator validator = new HttpUrlValidator();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.com",
                "https://example.com/a/b?x=1&y=2#frag",
                "HTTPS://Example.COM/Path",
                "https://sub.example.co.in:8443/path",
                "http://localhost:8080/x",
                "http://127.0.0.1/",
                "https://user:pw@example.com/",
                "https://example.com/%E0%A4%A8%E0%A4%AE"
            })
    void acceptsAbsoluteHttpAndHttpsUrlsWithHost(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "data:text/html,<b>hi</b>",
                "ftp://example.com/file",
                "mailto:someone@example.com",
                "/relative/path",
                "example.com/path",
                "www.example.com",
                "http://",
                "http:///path",
                "http:example.com",
                "https://exa mple.com",
                "https://example.com/a b",
                "https://example.com/\u0000",
                "http://[bad"
            })
    void rejectsNonHttpRelativeHostlessOrMalformedUrls(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void leavesNullAndBlankToNotBlank(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }
}
