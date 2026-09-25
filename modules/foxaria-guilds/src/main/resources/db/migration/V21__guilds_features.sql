CREATE TABLE IF NOT EXISTS fx_guild_activity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id VARCHAR(64) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_key VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

ALTER TABLE fx_guilds ADD COLUMN motd TEXT NOT NULL DEFAULT 'Добро пожаловать в гильдию!';
ALTER TABLE fx_guilds ADD COLUMN friendly_fire INTEGER NOT NULL DEFAULT 0;
