-- Stock Service - Initial Schema

CREATE TABLE product_stocks (
    product_id          BIGINT PRIMARY KEY,
    available_quantity  INT NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity   INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version             BIGINT DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TODO: stock_reservations tablosu (orderId UNIQUE) - compensation için.
-- Day 2'de eklenecek.

-- Seed data: 50 ürün için stok
INSERT INTO product_stocks (product_id, available_quantity) VALUES
    (1, 100), (2, 50), (3, 200), (4, 75), (5, 30),
    (6, 150), (7, 80), (8, 60), (9, 90), (10, 120),
    (11, 45), (12, 110), (13, 70), (14, 95), (15, 85),
    (16, 55), (17, 105), (18, 65), (19, 130), (20, 40),
    (21, 100), (22, 50), (23, 200), (24, 75), (25, 30),
    (26, 150), (27, 80), (28, 60), (29, 90), (30, 120),
    (31, 45), (32, 110), (33, 70), (34, 95), (35, 85),
    (36, 55), (37, 105), (38, 65), (39, 130), (40, 40),
    (41, 100), (42, 50), (43, 200), (44, 75), (45, 30),
    (46, 150), (47, 80), (48, 60), (49, 90), (50, 120);
