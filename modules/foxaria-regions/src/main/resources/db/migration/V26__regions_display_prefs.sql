ALTER TABLE fx_regions ADD COLUMN display_name VARCHAR(128) NULL;

ALTER TABLE fx_regions ADD UNIQUE INDEX uq_fx_regions_display_name (display_name);

CREATE TABLE IF NOT EXISTS fx_region_member_prefs (
    region_id BIGINT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    hide_boundary_particles TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (region_id, member_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
