CREATE TABLE IF NOT EXISTS fx_modern_furnace (
    world VARCHAR(36) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    data_json TEXT NOT NULL,
    updated_ms BIGINT NOT NULL,
    PRIMARY KEY (world, x, y, z)
);

CREATE INDEX IF NOT EXISTS idx_modern_furnace_world_chunk
    ON fx_modern_furnace (world, x, z);
