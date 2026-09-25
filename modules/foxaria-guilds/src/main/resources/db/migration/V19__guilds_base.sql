CREATE TABLE IF NOT EXISTS fx_guilds (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    owner_uuid VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    bank_balance VARCHAR(64) NOT NULL,
    guild_coins BIGINT NOT NULL DEFAULT 0,
    guild_points BIGINT NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    chest_rows INTEGER NOT NULL DEFAULT 3,
    shop_tier INTEGER NOT NULL DEFAULT 1,
    member_slots_bonus INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fx_guild_members (
    guild_id VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(64) NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, player_uuid),
    UNIQUE (player_uuid)
);

CREATE TABLE IF NOT EXISTS fx_guild_roles (
    guild_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    weight INTEGER NOT NULL,
    flags TEXT NOT NULL,
    PRIMARY KEY (guild_id, role_id)
);

CREATE TABLE IF NOT EXISTS fx_guild_invites (
    guild_id VARCHAR(64) NOT NULL,
    target_uuid VARCHAR(64) NOT NULL,
    inviter_uuid VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, target_uuid)
);

CREATE TABLE IF NOT EXISTS fx_guild_upgrades (
    guild_id VARCHAR(64) NOT NULL,
    upgrade_key VARCHAR(64) NOT NULL,
    level INTEGER NOT NULL,
    PRIMARY KEY (guild_id, upgrade_key)
);

CREATE TABLE IF NOT EXISTS fx_guild_chest_items (
    guild_id VARCHAR(64) NOT NULL,
    slot INTEGER NOT NULL,
    item_data TEXT NOT NULL,
    PRIMARY KEY (guild_id, slot)
);

CREATE TABLE IF NOT EXISTS fx_guild_shop_unlocks (
    guild_id VARCHAR(64) NOT NULL,
    unlock_key VARCHAR(64) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, unlock_key)
);
