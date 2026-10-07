package com.interviewprep.dto.link;

/** Outcome of a shorten request: {@code created} is false when an identical link already existed. */
public record ShortenResult(ShortLinkResponse link, boolean created) {}
