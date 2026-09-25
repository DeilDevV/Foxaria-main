CREATE TABLE IF NOT EXISTS fx_auction_listings (
    id VARCHAR(36) PRIMARY KEY,
    seller_uuid VARCHAR(36) NOT NULL,
    buyer_uuid VARCHAR(36),
    item_base64 TEXT NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    fee DECIMAL(20, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at BIGINT NOT NULL,
    claimed_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS fx_mailbox_deliveries (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    payload_base64 TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    claimed_at BIGINT NOT NULL
);
