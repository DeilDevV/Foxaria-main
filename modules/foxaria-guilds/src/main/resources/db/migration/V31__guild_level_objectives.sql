CREATE TABLE IF NOT EXISTS fx_guild_level_objective_progress (
    guild_id VARCHAR(64) NOT NULL,
    level INTEGER NOT NULL,
    objective_index INTEGER NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, level, objective_index)
);
