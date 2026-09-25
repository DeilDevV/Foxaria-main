CREATE TABLE IF NOT EXISTS fx_reward_claims (
    player_uuid VARCHAR(36) NOT NULL,
    reward_key VARCHAR(128) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, reward_key)
);

CREATE TABLE IF NOT EXISTS fx_crate_open_logs (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    crate_id VARCHAR(64) NOT NULL,
    reward_id VARCHAR(64) NOT NULL,
    action VARCHAR(255) NOT NULL,
    opened_at BIGINT NOT NULL
);
