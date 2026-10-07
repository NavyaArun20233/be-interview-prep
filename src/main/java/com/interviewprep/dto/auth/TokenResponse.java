package com.interviewprep.dto.auth;

import java.time.Instant;

/**
 * Bearer access token returned by login.
 *
 * @param expiresIn lifetime in seconds from issue
 * @param expiresAt absolute expiry (the token's {@code exp} claim)
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {

    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", expiresAt="
                + expiresAt + "]";
    }
}
