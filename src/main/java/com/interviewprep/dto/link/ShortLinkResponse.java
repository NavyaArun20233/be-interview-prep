package com.interviewprep.dto.link;

import java.time.Instant;

public record ShortLinkResponse(
        String code, String shortUrl, String originalUrl, Instant createdAt, Instant expiresAt) {}
