-- Sleepers (offline bodies) for anarchy server.
-- Stores full inventory snapshot for one of two outcomes:
-- 1) Player rejoins -> sleeper removed -> items restored to player.
-- 2) Sleeper killed -> items dropped -> player rejoins with empty inventory.

CREATE TABLE IF NOT EXISTS fx_sleepers (
  player_uuid VARCHAR(36) PRIMARY KEY,
  player_name VARCHAR(128) NOT NULL,
  world VARCHAR(128) NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  yaw DOUBLE NOT NULL,
  pitch DOUBLE NOT NULL,
  created_at BIGINT NOT NULL,
  entity_uuid VARCHAR(36),
  state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS fx_sleeper_items (
  player_uuid VARCHAR(36) NOT NULL,
  slot INT NOT NULL,
  item_base64 TEXT NOT NULL,
  PRIMARY KEY(player_uuid, slot)
);

