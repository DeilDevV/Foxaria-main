CREATE TABLE IF NOT EXISTS fx_custom_crafts (
    craft_id VARCHAR(64) PRIMARY KEY,
    recipe_json TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);
