CREATE TABLE IF NOT EXISTS fx_guild_level_rewards_claimed (
    guild_id VARCHAR(64) NOT NULL,
    level INTEGER NOT NULL,
    claimed_by_uuid VARCHAR(64) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, level)
);
