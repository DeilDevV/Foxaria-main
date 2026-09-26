CREATE TABLE IF NOT EXISTS fx_regions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    center_x INT NOT NULL,
    center_z INT NOT NULL,
    half_size INT NOT NULL,
    level INT NOT NULL DEFAULT 1,
    owner_uuid VARCHAR(36) NOT NULL,
    cabinet_x INT NOT NULL,
    cabinet_y INT NOT NULL,
    cabinet_z INT NOT NULL,
    core_hp INT NOT NULL,
    core_max_hp INT NOT NULL,
    core_last_damage_ms BIGINT NOT NULL DEFAULT 0,
    deposited_wood INT NOT NULL DEFAULT 0,
    deposited_iron INT NOT NULL DEFAULT 0,
    flags INT NOT NULL DEFAULT 3,
    INDEX idx_fx_regions_world (world)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_region_members (
    region_id BIGINT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    PRIMARY KEY (region_id, member_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_region_damaged_blocks (
    region_id BIGINT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    material VARCHAR(128) NOT NULL,
    current_hp INT NOT NULL,
    max_hp INT NOT NULL,
    last_damage_ms BIGINT NOT NULL,
    PRIMARY KEY (region_id, x, y, z)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
