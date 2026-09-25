CREATE TABLE IF NOT EXISTS fx_shop_categories (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS fx_shop_offers (
    id VARCHAR(128) PRIMARY KEY,
    category_id VARCHAR(64) NOT NULL,
    material VARCHAR(64) NOT NULL,
    amount INT NOT NULL,
    buy_price DECIMAL(20, 2) NOT NULL,
    sell_price DECIMAL(20, 2) NOT NULL
);
