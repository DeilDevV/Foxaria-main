CREATE TABLE IF NOT EXISTS fx_guild_activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_key VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE fx_guilds ADD COLUMN motd TEXT NOT NULL DEFAULT 'Добро пожаловать в гильдию!';
ALTER TABLE fx_guilds ADD COLUMN friendly_fire TINYINT(1) NOT NULL DEFAULT 0;
