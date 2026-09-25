CREATE TABLE IF NOT EXISTS fx_store_subscriptions (
    external_txn_id VARCHAR(128) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    package_id VARCHAR(128) NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    started_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at BIGINT NOT NULL
);
