-- =================================================================
-- Order Service - Initial Schema (Flyway V1)
-- =================================================================

CREATE TABLE orders (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     VARCHAR(255) NOT NULL,
    username                    VARCHAR(255),
    idempotency_key             VARCHAR(64) NOT NULL,
    status                      VARCHAR(32) NOT NULL,
    total_amount                NUMERIC(19,2) NOT NULL,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'TRY',
    ship_first_name             VARCHAR(255),
    ship_last_name              VARCHAR(255),
    ship_email                  VARCHAR(255),
    ship_phone                  VARCHAR(255),
    ship_address                VARCHAR(500),
    ship_city                   VARCHAR(255),
    ship_country                VARCHAR(255),
    payment_id                  VARCHAR(128),
    payment_failure_reason      VARCHAR(500),
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP,
    version                     BIGINT DEFAULT 0,
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_orders_user_id    ON orders(user_id);
CREATE INDEX idx_orders_status     ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

CREATE TABLE order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL,
    product_name    VARCHAR(255) NOT NULL,
    unit_price      NUMERIC(19,2) NOT NULL,
    quantity        INT NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id   ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- =================================================================
-- OUTBOX
-- =================================================================
CREATE TABLE outbox_events (
    id                  BIGSERIAL PRIMARY KEY,
    aggregate_type      VARCHAR(64)  NOT NULL,
    aggregate_id        VARCHAR(64)  NOT NULL,
    event_type          VARCHAR(64)  NOT NULL,
    routing_key         VARCHAR(128) NOT NULL,
    payload             TEXT         NOT NULL,
    published           BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at        TIMESTAMP,
    retry_count         INT          NOT NULL DEFAULT 0,
    last_error          VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Publisher'ın hızlı sorgu yapması için
CREATE INDEX idx_outbox_published_created ON outbox_events(published, created_at) WHERE published = FALSE;
CREATE INDEX idx_outbox_aggregate         ON outbox_events(aggregate_type, aggregate_id);
