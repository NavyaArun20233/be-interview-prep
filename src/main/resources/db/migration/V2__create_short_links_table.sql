CREATE TABLE short_links (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(8)    NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    -- SHA-256 hex of original_url: a fixed-size key for the idempotency index (the URL itself is too long to index).
    url_hash     VARCHAR(64)   NOT NULL,
    expires_at   TIMESTAMPTZ,
    visit_count  BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_short_links_code UNIQUE (code),
    -- One link per (URL, expiry); NULLS NOT DISTINCT makes two "never expires" links for the same URL conflict too.
    CONSTRAINT uq_short_links_url_hash_expires_at UNIQUE NULLS NOT DISTINCT (url_hash, expires_at),
    CONSTRAINT chk_short_links_code CHECK (code ~ '^[A-Za-z0-9]{1,8}$'),
    CONSTRAINT chk_short_links_visit_count CHECK (visit_count >= 0)
);
