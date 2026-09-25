CREATE TABLE IF NOT EXISTS fx_custom_item_instances (
    id VARCHAR(36) PRIMARY KEY,
    item_type VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL,
    consumed_at BIGINT NOT NULL DEFAULT 0
);
