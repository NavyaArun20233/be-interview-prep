package com.interviewprep.dto.link;

import com.interviewprep.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request body for shortening a URL. {@code url} is stripped of surrounding whitespace and otherwise stored exactly as
 * submitted; {@code expiresAt} is optional (ISO-8601 with offset) and must be in the future.
 */
public record CreateShortLinkRequest(
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        @Future OffsetDateTime expiresAt) {

    public CreateShortLinkRequest {
        url = url == null ? null : url.strip();
    }
}
