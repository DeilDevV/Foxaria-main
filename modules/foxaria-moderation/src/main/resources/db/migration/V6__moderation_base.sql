CREATE TABLE IF NOT EXISTS fx_punishments (
    id VARCHAR(36) PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    actor_uuid VARCHAR(36),
    type VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_reports (
    id VARCHAR(36) PRIMARY KEY,
    reporter_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_staff_notes (
    id VARCHAR(36) PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    actor_uuid VARCHAR(36) NOT NULL,
    note TEXT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
