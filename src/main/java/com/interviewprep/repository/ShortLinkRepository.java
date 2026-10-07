package com.interviewprep.repository;

import com.interviewprep.entity.ShortLink;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    boolean existsByCode(String code);

    @Query("select l.originalUrl from ShortLink l where l.code = :code")
    Optional<String> findOriginalUrlByCode(@Param("code") String code);

    Optional<ShortLink> findByUrlHashAndExpiresAt(String urlHash, Instant expiresAt);

    Optional<ShortLink> findByUrlHashAndExpiresAtIsNull(String urlHash);

    /**
     * Inserts the link unless it conflicts with an existing row on <em>either</em> unique constraint (code, or URL hash
     * + expiry). Returns 1 if inserted, 0 on conflict. Using {@code DO NOTHING} instead of letting the insert fail keeps
     * the surrounding PostgreSQL transaction usable, so the caller can look up the existing row or retry with another
     * code in the same transaction. If a concurrent transaction holds a conflicting uncommitted row, PostgreSQL waits for
     * it to finish before deciding, so a follow-up read (READ COMMITTED) sees the winner.
     */
    @Modifying
    @Query(value = """
                    INSERT INTO short_links (code, original_url, url_hash, expires_at, visit_count, created_at)
                    VALUES (:code, :originalUrl, :urlHash, :expiresAt, 0, :createdAt)
                    ON CONFLICT DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("code") String code,
            @Param("originalUrl") String originalUrl,
            @Param("urlHash") String urlHash,
            @Param("expiresAt") Instant expiresAt,
            @Param("createdAt") Instant createdAt);

    default int insertIfAbsent(ShortLink link) {
        return insertIfAbsent(
                link.getCode(), link.getOriginalUrl(), link.getUrlHash(), link.getExpiresAt(), link.getCreatedAt());
    }

    /**
     * Counts one visit in a single atomic statement (no read-modify-write), so concurrent visits are never lost. Returns
     * 1 if the link exists and is active at {@code now}, otherwise 0.
     */
    @Modifying
    @Query("""
            update ShortLink l set l.visitCount = l.visitCount + 1
            where l.code = :code and (l.expiresAt is null or l.expiresAt > :now)
            """)
    int incrementVisitCountIfActive(@Param("code") String code, @Param("now") Instant now);
}
