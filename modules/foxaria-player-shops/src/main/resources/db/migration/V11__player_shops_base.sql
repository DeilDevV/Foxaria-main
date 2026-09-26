CREATE TABLE IF NOT EXISTS fx_player_shops (
    id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    open TINYINT(1) NOT NULL,
    tax_percent DOUBLE NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_player_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(36) NOT NULL,
    item_base64 TEXT NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    stock INT NOT NULL,
    active TINYINT(1) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
