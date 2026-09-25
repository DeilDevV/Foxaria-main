CREATE TABLE IF NOT EXISTS fx_login_streaks (
    player_uuid VARCHAR(36) PRIMARY KEY,
    last_login_date VARCHAR(16) NOT NULL,
    streak_days INT NOT NULL
);

CREATE TABLE IF NOT EXISTS fx_playtime_rewards (
    player_uuid VARCHAR(36) NOT NULL,
    reward_key BIGINT NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, reward_key)
);
