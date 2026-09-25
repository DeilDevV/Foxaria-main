CREATE TABLE IF NOT EXISTS fx_security_events (
    id VARCHAR(36) PRIMARY KEY,
    actor_uuid VARCHAR(36),
    event_type VARCHAR(64) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL
);
