CREATE TABLE products (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(200)   NOT NULL,
    category   VARCHAR(50)    NOT NULL,
    price      NUMERIC(10, 2) NOT NULL,
    stock      INT            NOT NULL,
    rating     NUMERIC(2, 1)  NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock CHECK (stock >= 0),
    CONSTRAINT chk_products_rating CHECK (rating BETWEEN 0 AND 5)
);

-- Columns used by the list filters and common sorts.
CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_price ON products (price);
CREATE INDEX idx_products_created_at ON products (created_at);

-- Deterministic seed data: 100 products over 5 categories; every 7th product is out of stock.
INSERT INTO products (name, category, price, stock, rating, created_at, updated_at)
SELECT 'Product ' || LPAD(n::TEXT, 3, '0'),
       (ARRAY ['ELECTRONICS', 'BOOKS', 'CLOTHING', 'HOME', 'SPORTS'])[(n - 1) % 5 + 1],
       ROUND((5 + (n * 37) % 500 + (n % 100) / 100.0)::NUMERIC, 2),
       CASE WHEN n % 7 = 0 THEN 0 ELSE (n * 13) % 200 + 1 END,
       ROUND(((n * 3) % 51 / 10.0)::NUMERIC, 1),
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + n * INTERVAL '1 hour',
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + n * INTERVAL '1 hour'
FROM generate_series(1, 100) AS n;
