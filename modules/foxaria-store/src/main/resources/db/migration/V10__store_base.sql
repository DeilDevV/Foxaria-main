CREATE TABLE IF NOT EXISTS fx_tebex_fulfillments (
    id VARCHAR(36) PRIMARY KEY,
    external_txn_id VARCHAR(128) NOT NULL UNIQUE,
    player_uuid VARCHAR(36) NOT NULL,
    package_id VARCHAR(128) NOT NULL,
    action VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    payload_json TEXT NOT NULL,
    attempts INT NOT NULL,
    last_error TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
