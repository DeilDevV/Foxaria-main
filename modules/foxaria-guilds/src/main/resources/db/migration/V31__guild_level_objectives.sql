CREATE TABLE IF NOT EXISTS fx_guild_level_objective_progress (
    guild_id VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    objective_index INT NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, level, objective_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
