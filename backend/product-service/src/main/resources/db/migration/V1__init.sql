CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(100) NOT NULL,
    brand       VARCHAR(100),
    base_price  NUMERIC(19, 2) NOT NULL,
    image_url   VARCHAR(500),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE
);

-- Her ürünün farklı dillerdeki adı ve açıklaması burada tutulur.
-- (product_id, language) çifti unique: aynı dilde iki çeviri olamaz.
CREATE TABLE product_translations (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    language    VARCHAR(5) NOT NULL,     -- 'tr', 'en', 'de' ...
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    CONSTRAINT uq_product_translation UNIQUE (product_id, language)
);

CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_active    ON products(active);
CREATE INDEX idx_translations_lang  ON product_translations(product_id, language);
