-- products.stock >= 0 is already enforced by chk_products_stock (V4): the database refuses any oversell.

CREATE TABLE orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    -- Client-chosen retry key, unique per user: a retried POST finds the order created by the first attempt.
    idempotency_key VARCHAR(100)   NOT NULL,
    -- SHA-256 (hex) of the canonical request, to reject reuse of a key with a different request.
    request_hash    VARCHAR(64)    NOT NULL,
    total_amount    NUMERIC(12, 2) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- Its index (leading column user_id) also serves lookups by user, so there is no separate user_id index.
    CONSTRAINT uq_orders_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_orders_status CHECK (status IN ('PLACED', 'CANCELLED')),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0)
);

CREATE TABLE order_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    -- Price at the time the order was placed.
    unit_price NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    -- Duplicate product lines are merged before insert; this index (leading column order_id) serves lookups by order.
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_product_id ON order_items (product_id);
