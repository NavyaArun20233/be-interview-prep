package com.interviewprep.link;

import com.interviewprep.common.error.FieldValidationException;
import com.interviewprep.link.dto.CreateShortLinkRequest;
import com.interviewprep.link.dto.ShortLinkResponse;
import com.interviewprep.link.dto.ShortLinkStatsResponse;
import com.interviewprep.link.dto.ShortenResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    /** Bounded retries on a short-code collision; with 62^7 codes even one retry is rare. */
    static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortLinkRepository shortLinkRepository;
    private final ShortCodeGenerator codeGenerator;
    private final ShortLinkProperties properties;
    private final Clock clock;

    public ShortLinkService(
            ShortLinkRepository shortLinkRepository,
            ShortCodeGenerator codeGenerator,
            ShortLinkProperties properties,
            Clock clock) {
        this.shortLinkRepository = shortLinkRepository;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Shortens a URL idempotently: the same URL with the same expiry (or both without one) returns the existing link
     * instead of a new code, so callers get a stable code and the table holds no duplicates; a different expiry is a
     * different lifetime and gets its own link. Because the requested expiry must be in the future, a matching existing
     * link is never expired. The rule is enforced by a unique constraint, so concurrent identical requests also receive
     * the same code.
     */
    @Transactional
    public ShortenResult shorten(CreateShortLinkRequest request) {
        Instant now = now();
        String url = request.url();
        Instant expiresAt = toExpiry(request.expiresAt(), now);
        String urlHash = sha256Hex(url);

        Optional<ShortLink> existing = findExisting(url, urlHash, expiresAt);
        if (existing.isPresent()) {
            return new ShortenResult(toResponse(existing.get()), false);
        }
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            ShortLink link = new ShortLink(codeGenerator.generate(), url, urlHash, expiresAt, now);
            if (shortLinkRepository.insertIfAbsent(link) == 1) {
                log.info("Created short link {}", link.getCode());
                return new ShortenResult(toResponse(link), true);
            }
            // Nothing inserted: either a concurrent request created this (URL, expiry) link, or the code is taken.
            existing = findExisting(url, urlHash, expiresAt);
            if (existing.isPresent()) {
                return new ShortenResult(toResponse(existing.get()), false);
            }
            log.warn("Short code collision on attempt {} of {}", attempt, MAX_CODE_ATTEMPTS);
        }
        throw new IllegalStateException(
                "Could not generate a unique short code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    /** Counts a visit and returns the URL to redirect to. Visits to unknown or expired links are not counted. */
    @Transactional
    public String visit(String code) {
        if (shortLinkRepository.incrementVisitCountIfActive(code, now()) == 1) {
            return shortLinkRepository
                    .findOriginalUrlByCode(code)
                    .orElseThrow(() -> new ShortLinkNotFoundException(code));
        }
        // Links are never deleted, so an existing code that was not counted has expired.
        if (shortLinkRepository.existsByCode(code)) {
            throw new ShortLinkExpiredException(code);
        }
        throw new ShortLinkNotFoundException(code);
    }

    /** Stats stay available after a link expires. */
    @Transactional(readOnly = true)
    public ShortLinkStatsResponse stats(String code) {
        ShortLink link = shortLinkRepository.findByCode(code).orElseThrow(() -> new ShortLinkNotFoundException(code));
        return new ShortLinkStatsResponse(
                link.getCode(),
                properties.shortUrl(link.getCode()),
                link.getOriginalUrl(),
                link.getVisitCount(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.isExpired(now()));
    }

    /**
     * Re-checks the request's {@code @Future} at the service's "now", so a request validated just before the expiry
     * instant cannot store an already expired link (which would also break "a matching existing link is never expired").
     */
    private static Instant toExpiry(OffsetDateTime requested, Instant now) {
        if (requested == null) {
            return null;
        }
        // Truncate like PostgreSQL TIMESTAMPTZ so lookups compare equal to what is stored.
        Instant expiresAt = requested.toInstant().truncatedTo(ChronoUnit.MICROS);
        if (!expiresAt.isAfter(now)) {
            throw new FieldValidationException("expiresAt", "must be a future date");
        }
        return expiresAt;
    }

    private Optional<ShortLink> findExisting(String url, String urlHash, Instant expiresAt) {
        Optional<ShortLink> found = expiresAt == null
                ? shortLinkRepository.findByUrlHashAndExpiresAtIsNull(urlHash)
                : shortLinkRepository.findByUrlHashAndExpiresAt(urlHash, expiresAt);
        // A SHA-256 collision is not a practical concern, but never hand out a link to a different URL.
        if (found.isPresent() && !found.get().getOriginalUrl().equals(url)) {
            throw new IllegalStateException(
                    "URL hash collision with short link " + found.get().getCode());
        }
        return found;
    }

    private ShortLinkResponse toResponse(ShortLink link) {
        return new ShortLinkResponse(
                link.getCode(),
                properties.shortUrl(link.getCode()),
                link.getOriginalUrl(),
                link.getCreatedAt(),
                link.getExpiresAt());
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
