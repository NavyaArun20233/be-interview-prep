CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Stored trimmed and lower-cased by the application, so the unique constraint is case-insensitive in practice.
    email         VARCHAR(254)  NOT NULL,
    -- Delegating password encoder format, e.g. "{bcrypt}$2a$10$..." (68 characters for bcrypt).
    password_hash VARCHAR(100)  NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);
