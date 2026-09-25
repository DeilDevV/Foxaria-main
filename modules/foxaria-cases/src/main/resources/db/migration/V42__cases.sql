CREATE TABLE IF NOT EXISTS fx_case_keys (
    player_uuid VARCHAR(36) NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    amount INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, case_id)
);

CREATE TABLE IF NOT EXISTS fx_placed_cases (
    server_id VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (server_id, world, x, y, z)
);

CREATE TABLE IF NOT EXISTS fx_case_pending_rewards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    target_server VARCHAR(64) NOT NULL,
    commands_json TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_case_pending_player ON fx_case_pending_rewards (player_uuid, target_server);
