CREATE TABLE IF NOT EXISTS fx_economy_accounts (
    player_uuid VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(20, 2) NOT NULL,
    tokens BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS fx_economy_transactions (
    id VARCHAR(36) PRIMARY KEY,
    actor_uuid VARCHAR(36),
    target_uuid VARCHAR(36),
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    fee DECIMAL(20, 2) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    metadata_json TEXT,
    created_at BIGINT NOT NULL
);
