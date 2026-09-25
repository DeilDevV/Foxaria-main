CREATE TABLE IF NOT EXISTS fx_regions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world VARCHAR(64) NOT NULL,
    center_x INTEGER NOT NULL,
    center_z INTEGER NOT NULL,
    half_size INTEGER NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    owner_uuid VARCHAR(36) NOT NULL,
    cabinet_x INTEGER NOT NULL,
    cabinet_y INTEGER NOT NULL,
    cabinet_z INTEGER NOT NULL,
    core_hp INTEGER NOT NULL,
    core_max_hp INTEGER NOT NULL,
    core_last_damage_ms BIGINT NOT NULL DEFAULT 0,
    deposited_wood INTEGER NOT NULL DEFAULT 0,
    deposited_iron INTEGER NOT NULL DEFAULT 0,
    flags INTEGER NOT NULL DEFAULT 3
);

CREATE TABLE IF NOT EXISTS fx_region_members (
    region_id INTEGER NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    PRIMARY KEY (region_id, member_uuid)
);

CREATE TABLE IF NOT EXISTS fx_region_damaged_blocks (
    region_id INTEGER NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    material VARCHAR(128) NOT NULL,
    current_hp INTEGER NOT NULL,
    max_hp INTEGER NOT NULL,
    last_damage_ms BIGINT NOT NULL,
    PRIMARY KEY (region_id, x, y, z)
);

CREATE INDEX IF NOT EXISTS idx_fx_regions_world ON fx_regions(world);
