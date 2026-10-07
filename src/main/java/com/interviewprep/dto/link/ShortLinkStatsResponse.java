package com.interviewprep.dto.link;

import java.time.Instant;

/** {@code visitCount} counts successful redirects; visits to an expired link are not counted. */
public record ShortLinkStatsResponse(
        String code,
        String shortUrl,
        String originalUrl,
        long visitCount,
        Instant createdAt,
        Instant expiresAt,
        boolean expired) {}
