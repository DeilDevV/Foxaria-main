CREATE TABLE IF NOT EXISTS fx_player_entry_points (
    player_uuid VARCHAR(36) PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    updated_at BIGINT NOT NULL
);
