-- V2: Stock Reservations tablosu
-- Compensation (geri alma) için orderId → (productId, quantity) eşlemesi tutuyoruz.
-- PaymentFailedEvent geldiğinde hangi ürünün kaç adedini geri açacağımızı buradan buluruz.
-- orderId UNIQUE constraint → idempotency: aynı siparişi iki kez rezerve etmez.

CREATE TABLE stock_reservations (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Aynı orderId iki kez işlenemez (idempotency)
    CONSTRAINT uq_reservation_order_product UNIQUE (order_id, product_id)
);

-- order_id üzerinden hızlı lookup (compensation anında)
CREATE INDEX idx_stock_reservations_order_id ON stock_reservations(order_id);
