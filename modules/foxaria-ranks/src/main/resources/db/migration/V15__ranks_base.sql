CREATE TABLE IF NOT EXISTS fx_player_ranks (
    player_uuid VARCHAR(36) PRIMARY KEY,
    primary_group VARCHAR(64) NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_rank_grants (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    granted_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL,
    INDEX idx_fx_rank_grants_player (player_uuid, active, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_permission_grants (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    permission VARCHAR(128) NOT NULL,
    reason VARCHAR(255),
    granted_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL,
    INDEX idx_fx_permission_grants_player (player_uuid, active, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
