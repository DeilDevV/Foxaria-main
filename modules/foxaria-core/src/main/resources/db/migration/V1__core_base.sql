CREATE TABLE IF NOT EXISTS fx_players (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(16) NOT NULL,
    first_join_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    playtime_seconds BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fx_player_homes (
    player_uuid VARCHAR(36) NOT NULL,
    home_name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, home_name)
);

CREATE TABLE IF NOT EXISTS fx_spawn_points (
    spawn_key VARCHAR(32) PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS fx_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    actor_uuid VARCHAR(36),
    target_uuid VARCHAR(36),
    actor_name VARCHAR(16),
    target_name VARCHAR(16),
    summary VARCHAR(255) NOT NULL,
    metadata_json TEXT,
    created_at BIGINT NOT NULL
);
