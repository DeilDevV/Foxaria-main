CREATE TABLE IF NOT EXISTS fx_guild_war_arenas (
    arena_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    spawn_a TEXT NOT NULL,
    spawn_b TEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_guild_war_matches (
    match_id VARCHAR(64) PRIMARY KEY,
    guild_a_id VARCHAR(64) NOT NULL,
    guild_b_id VARCHAR(64) NOT NULL,
    arena_id VARCHAR(64) NOT NULL,
    team_size INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_guild_war_queue (
    guild_id VARCHAR(64) PRIMARY KEY,
    team_size INT NOT NULL,
    queued_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
