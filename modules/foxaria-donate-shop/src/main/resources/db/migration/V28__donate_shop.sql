CREATE TABLE IF NOT EXISTS fx_donate_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    price_tokens BIGINT NOT NULL,
    item_blob TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_donate_shop_category ON fx_donate_shop_offers (category, sort_order);
