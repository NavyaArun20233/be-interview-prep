package com.interviewprep.entity;

import com.interviewprep.repository.ShortLinkRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A short code mapped to an original URL.
 *
 * <p>Rows are inserted with {@link ShortLinkRepository#insertIfAbsent} and {@code visitCount} is only changed by the
 * atomic {@link ShortLinkRepository#incrementVisitCountIfActive}; the entity itself is never saved after loading, so it
 * has no {@code @Version} (optimistic locking on a hot counter would turn concurrent visits into conflicts).
 */
@Entity
@Table(name = "short_links")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "visit_count", nullable = false)
    private long visitCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShortLink() {
        // for JPA
    }

    public ShortLink(String code, String originalUrl, String urlHash, Instant expiresAt, Instant createdAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.urlHash = urlHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    /** Active links have no expiry or expire strictly after {@code now}; same rule as the visit-count update. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getVisitCount() {
        return visitCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
