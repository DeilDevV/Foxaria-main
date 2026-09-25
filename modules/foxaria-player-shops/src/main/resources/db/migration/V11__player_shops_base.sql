CREATE TABLE IF NOT EXISTS fx_player_shops (
    id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    open BOOLEAN NOT NULL,
    tax_percent DOUBLE NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS fx_player_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(36) NOT NULL,
    item_base64 TEXT NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    stock INT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at BIGINT NOT NULL
);
