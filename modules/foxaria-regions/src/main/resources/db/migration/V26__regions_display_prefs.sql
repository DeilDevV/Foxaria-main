ALTER TABLE fx_regions ADD COLUMN display_name VARCHAR(128) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fx_regions_display_name ON fx_regions (display_name);

CREATE TABLE IF NOT EXISTS fx_region_member_prefs (
    region_id INTEGER NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    hide_boundary_particles INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (region_id, member_uuid)
);
