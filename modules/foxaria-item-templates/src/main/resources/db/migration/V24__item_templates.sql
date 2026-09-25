CREATE TABLE IF NOT EXISTS fx_item_templates (
    id VARCHAR(64) PRIMARY KEY,
    stack_data TEXT NOT NULL,
    notes TEXT,
    updated_at BIGINT NOT NULL
);
