CREATE TABLE IF NOT EXISTS fx_kit_definitions (
    kit_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    cooldown_seconds BIGINT NOT NULL DEFAULT 0,
    permission VARCHAR(256) NOT NULL,
    required_playtime_seconds BIGINT NOT NULL DEFAULT 0,
    payload TEXT NOT NULL,
    updated_at BIGINT NOT NULL
);
