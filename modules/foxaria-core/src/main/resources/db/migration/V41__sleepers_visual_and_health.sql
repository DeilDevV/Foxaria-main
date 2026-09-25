-- Extend sleepers: separate hitbox entity + visual corpse + health + stored name prefix.

ALTER TABLE fx_sleepers ADD COLUMN zombie_uuid VARCHAR(36);
ALTER TABLE fx_sleepers ADD COLUMN armor_uuid VARCHAR(36);
ALTER TABLE fx_sleepers ADD COLUMN health DOUBLE NOT NULL DEFAULT 20.0;
ALTER TABLE fx_sleepers ADD COLUMN name_line VARCHAR(512) NOT NULL DEFAULT '';

