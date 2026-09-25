CREATE TABLE IF NOT EXISTS fx_kit_claims (
    player_uuid VARCHAR(36) NOT NULL,
    kit_id VARCHAR(64) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, kit_id)
);
