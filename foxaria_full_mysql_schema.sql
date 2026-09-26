-- Foxaria full native MySQL schema
-- Generated from repository migrations and proxy schemas
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE IF NOT EXISTS fx_schema_history (
  version INT PRIMARY KEY,
  description VARCHAR(255) NOT NULL,
  applied_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V1__core_base
CREATE TABLE IF NOT EXISTS fx_players (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(16) NOT NULL,
    first_join_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    playtime_seconds BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_player_homes (
    player_uuid VARCHAR(36) NOT NULL,
    home_name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, home_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_spawn_points (
    spawn_key VARCHAR(32) PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    actor_uuid VARCHAR(36),
    target_uuid VARCHAR(36),
    actor_name VARCHAR(16),
    target_name VARCHAR(16),
    summary VARCHAR(255) NOT NULL,
    metadata_json TEXT,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V2__economy_base
CREATE TABLE IF NOT EXISTS fx_economy_accounts (
    player_uuid VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(20, 2) NOT NULL,
    tokens BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_economy_transactions (
    id VARCHAR(36) PRIMARY KEY,
    actor_uuid VARCHAR(36),
    target_uuid VARCHAR(36),
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    fee DECIMAL(20, 2) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    metadata_json TEXT,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V3__kits_base
CREATE TABLE IF NOT EXISTS fx_kit_claims (
    player_uuid VARCHAR(36) NOT NULL,
    kit_id VARCHAR(64) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, kit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V4__shop_base
CREATE TABLE IF NOT EXISTS fx_shop_categories (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_shop_offers (
    id VARCHAR(128) PRIMARY KEY,
    category_id VARCHAR(64) NOT NULL,
    material VARCHAR(64) NOT NULL,
    amount INT NOT NULL,
    buy_price DECIMAL(20, 2) NOT NULL,
    sell_price DECIMAL(20, 2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V5__auction_base
CREATE TABLE IF NOT EXISTS fx_auction_listings (
    id VARCHAR(36) PRIMARY KEY,
    seller_uuid VARCHAR(36) NOT NULL,
    buyer_uuid VARCHAR(36),
    item_base64 TEXT NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    fee DECIMAL(20, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at BIGINT NOT NULL,
    claimed_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_mailbox_deliveries (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    payload_base64 TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    claimed_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V6__moderation_base
CREATE TABLE IF NOT EXISTS fx_punishments (
    id VARCHAR(36) PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    actor_uuid VARCHAR(36),
    type VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_reports (
    id VARCHAR(36) PRIMARY KEY,
    reporter_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_staff_notes (
    id VARCHAR(36) PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    actor_uuid VARCHAR(36) NOT NULL,
    note TEXT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V7__security_base
CREATE TABLE IF NOT EXISTS fx_security_events (
    id VARCHAR(36) PRIMARY KEY,
    actor_uuid VARCHAR(36),
    event_type VARCHAR(64) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V8__custom_items_base
CREATE TABLE IF NOT EXISTS fx_custom_item_instances (
    id VARCHAR(36) PRIMARY KEY,
    item_type VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL,
    consumed_at BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V9__retention_base
CREATE TABLE IF NOT EXISTS fx_login_streaks (
    player_uuid VARCHAR(36) PRIMARY KEY,
    last_login_date VARCHAR(16) NOT NULL,
    streak_days INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_playtime_rewards (
    player_uuid VARCHAR(36) NOT NULL,
    reward_key BIGINT NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, reward_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V10__store_base
CREATE TABLE IF NOT EXISTS fx_tebex_fulfillments (
    id VARCHAR(36) PRIMARY KEY,
    external_txn_id VARCHAR(128) NOT NULL UNIQUE,
    player_uuid VARCHAR(36) NOT NULL,
    package_id VARCHAR(128) NOT NULL,
    action VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    payload_json TEXT NOT NULL,
    attempts INT NOT NULL,
    last_error TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V11__player_shops_base
CREATE TABLE IF NOT EXISTS fx_player_shops (
    id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    open TINYINT(1) NOT NULL,
    tax_percent DOUBLE NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_player_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(36) NOT NULL,
    item_base64 TEXT NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    price DECIMAL(20, 2) NOT NULL,
    stock INT NOT NULL,
    active TINYINT(1) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V12__custom_items_rewards
CREATE TABLE IF NOT EXISTS fx_reward_claims (
    player_uuid VARCHAR(36) NOT NULL,
    reward_key VARCHAR(128) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, reward_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_crate_open_logs (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    crate_id VARCHAR(64) NOT NULL,
    reward_id VARCHAR(64) NOT NULL,
    action VARCHAR(255) NOT NULL,
    opened_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V13__store_lifecycle
CREATE TABLE IF NOT EXISTS fx_store_subscriptions (
    external_txn_id VARCHAR(128) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    package_id VARCHAR(128) NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    started_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V14__retention_community
CREATE TABLE IF NOT EXISTS fx_vote_claims (
    player_uuid VARCHAR(36) NOT NULL,
    site_key VARCHAR(64) NOT NULL,
    vote_date VARCHAR(16) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, site_key, vote_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_referrals (
    referred_uuid VARCHAR(36) PRIMARY KEY,
    referrer_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V15__ranks_base
CREATE TABLE IF NOT EXISTS fx_player_ranks (
    player_uuid VARCHAR(36) PRIMARY KEY,
    primary_group VARCHAR(64) NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_rank_grants (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    granted_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL,
    INDEX idx_fx_rank_grants_player (player_uuid, active, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_permission_grants (
    id VARCHAR(36) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    permission VARCHAR(128) NOT NULL,
    reason VARCHAR(255),
    granted_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL,
    INDEX idx_fx_permission_grants_player (player_uuid, active, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V16__auth_base
CREATE TABLE IF NOT EXISTS fx_auth_accounts (
    username VARCHAR(64) PRIMARY KEY,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    iterations INT NOT NULL,
    last_uuid VARCHAR(36),
    registered_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    last_login_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V17__auth_uuid_index
CREATE INDEX idx_fx_auth_last_uuid
    ON fx_auth_accounts(last_uuid);

-- V18__player_flow_entry_points
CREATE TABLE IF NOT EXISTS fx_player_entry_points (
    player_uuid VARCHAR(36) PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V19__guilds_base
CREATE TABLE IF NOT EXISTS fx_guilds (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    owner_uuid VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    bank_balance VARCHAR(64) NOT NULL,
    guild_coins BIGINT NOT NULL DEFAULT 0,
    guild_points BIGINT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    chest_rows INT NOT NULL DEFAULT 3,
    shop_tier INT NOT NULL DEFAULT 1,
    member_slots_bonus INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_members (
    guild_id VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(64) NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, player_uuid),
    UNIQUE (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_roles (
    guild_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    weight INT NOT NULL,
    flags TEXT NOT NULL,
    PRIMARY KEY (guild_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_invites (
    guild_id VARCHAR(64) NOT NULL,
    target_uuid VARCHAR(64) NOT NULL,
    inviter_uuid VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, target_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_upgrades (
    guild_id VARCHAR(64) NOT NULL,
    upgrade_key VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    PRIMARY KEY (guild_id, upgrade_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_chest_items (
    guild_id VARCHAR(64) NOT NULL,
    slot INT NOT NULL,
    item_data TEXT NOT NULL,
    PRIMARY KEY (guild_id, slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_shop_unlocks (
    guild_id VARCHAR(64) NOT NULL,
    unlock_key VARCHAR(64) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, unlock_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V20__guilds_war_prep
CREATE TABLE IF NOT EXISTS fx_guild_war_arenas (
    arena_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    spawn_a TEXT NOT NULL,
    spawn_b TEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_war_matches (
    match_id VARCHAR(64) PRIMARY KEY,
    guild_a_id VARCHAR(64) NOT NULL,
    guild_b_id VARCHAR(64) NOT NULL,
    arena_id VARCHAR(64) NOT NULL,
    team_size INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_guild_war_queue (
    guild_id VARCHAR(64) PRIMARY KEY,
    team_size INT NOT NULL,
    queued_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V21__guilds_features
CREATE TABLE IF NOT EXISTS fx_guild_activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_key VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE fx_guilds ADD COLUMN motd TEXT NOT NULL DEFAULT 'Добро пожаловать в гильдию!';
ALTER TABLE fx_guilds ADD COLUMN friendly_fire TINYINT(1) NOT NULL DEFAULT 0;

-- V22__guild_levels_rewards
CREATE TABLE IF NOT EXISTS fx_guild_level_rewards_claimed (
    guild_id VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    claimed_by_uuid VARCHAR(64) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V23__guild_tag_and_progress
ALTER TABLE fx_guilds ADD COLUMN tag_color VARCHAR(32) NOT NULL DEFAULT 'WHITE';
CREATE TABLE IF NOT EXISTS fx_guild_stats (
    guild_id VARCHAR(64) PRIMARY KEY,
    total_kills BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V24__item_templates
CREATE TABLE IF NOT EXISTS fx_item_templates (
    id VARCHAR(64) PRIMARY KEY,
    stack_data TEXT NOT NULL,
    notes TEXT,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V25__regions
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

-- V26__regions_display_prefs
ALTER TABLE fx_regions ADD COLUMN display_name VARCHAR(128) NULL;
ALTER TABLE fx_regions ADD UNIQUE INDEX uq_fx_regions_display_name (display_name);
CREATE TABLE IF NOT EXISTS fx_region_member_prefs (
    region_id BIGINT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    hide_boundary_particles TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (region_id, member_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V27__kit_definitions
CREATE TABLE IF NOT EXISTS fx_kit_definitions (
    kit_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    cooldown_seconds BIGINT NOT NULL DEFAULT 0,
    permission VARCHAR(256) NOT NULL,
    required_playtime_seconds BIGINT NOT NULL DEFAULT 0,
    payload TEXT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V28__donate_shop
CREATE TABLE IF NOT EXISTS fx_donate_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    price_tokens BIGINT NOT NULL,
    item_blob TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    INDEX idx_donate_shop_category (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V29__progression_quest_shop
CREATE TABLE IF NOT EXISTS fx_player_progression (
    player_uuid VARCHAR(36) PRIMARY KEY,
    knowledge_level INT NOT NULL DEFAULT 1,
    current_quest_id VARCHAR(64),
    quest_progress BIGINT NOT NULL DEFAULT 0,
    completed_quests TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_progression_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    required_knowledge INT NOT NULL DEFAULT 1,
    price DECIMAL(20, 2) NOT NULL,
    item_blob TEXT,
    item_template VARCHAR(64) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    INDEX idx_prog_shop_cat (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V30__quest_objective_progress
ALTER TABLE fx_player_progression ADD COLUMN quest_objective_csv TEXT DEFAULT NULL;

-- V31__guild_level_objectives
CREATE TABLE IF NOT EXISTS fx_guild_level_objective_progress (
    guild_id VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    objective_index INT NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, level, objective_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V32__guild_quest_chain_reset
DELETE FROM fx_guild_level_objective_progress;
DELETE FROM fx_guild_level_rewards_claimed;

-- V33__custom_crafts
CREATE TABLE IF NOT EXISTS fx_custom_crafts (
    craft_id VARCHAR(64) PRIMARY KEY,
    recipe_json TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V35__item_template_flags
ALTER TABLE fx_item_templates ADD COLUMN enchant_glint INTEGER NOT NULL DEFAULT 0;
ALTER TABLE fx_item_templates ADD COLUMN crafting_core INTEGER NOT NULL DEFAULT 0;

-- V36__modern_furnace
CREATE TABLE IF NOT EXISTS fx_modern_furnace (
    world VARCHAR(36) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    data_json TEXT NOT NULL,
    updated_ms BIGINT NOT NULL,
    PRIMARY KEY (world, x, y, z)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_modern_furnace_world_chunk
    ON fx_modern_furnace (world, x, z);

-- V37__craft_sulfur_info
INSERT IGNORE INTO fx_custom_crafts (craft_id, recipe_json, sort_order, updated_at)
VALUES (
    'sulfur_smelting',
    '{"creationKind":"FURNACE","registerPrimaryRecipe":false,"displayName":"&e&lСера &7при плавке руд и песка","requiredKnowledge":1,"resultTemplateId":"sulfur","resultAmount":1,"resultStackBase64":"","bonusTemplateId":"","bonusChancePercent":0,"smeltCookTicks":200,"smeltExperience":0.2,"grid":[null,null,null,null,null,null,null,null,null],"ingredients":[]}',
    0,
    0
);

-- V40__sleepers_base
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_sleeper_items (
  player_uuid VARCHAR(36) NOT NULL,
  slot INT NOT NULL,
  item_base64 TEXT NOT NULL,
  PRIMARY KEY(player_uuid, slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V41__sleepers_visual_and_health
ALTER TABLE fx_sleepers ADD COLUMN zombie_uuid VARCHAR(36);
ALTER TABLE fx_sleepers ADD COLUMN armor_uuid VARCHAR(36);
ALTER TABLE fx_sleepers ADD COLUMN health DOUBLE NOT NULL DEFAULT 20.0;
ALTER TABLE fx_sleepers ADD COLUMN name_line VARCHAR(512) NOT NULL DEFAULT '';

-- V42__cases
CREATE TABLE IF NOT EXISTS fx_case_keys (
    player_uuid VARCHAR(36) NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    amount INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_placed_cases (
    server_id VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (server_id, world, x, y, z)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fx_case_pending_rewards (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    target_server VARCHAR(64) NOT NULL,
    commands_json TEXT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_case_pending_player ON fx_case_pending_rewards (player_uuid, target_server);

-- Proxy schemas
CREATE TABLE IF NOT EXISTS proxy_accounts (
  player_uuid VARCHAR(36) PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  last_login_at BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS proxy_sessions (
  player_uuid VARCHAR(36) PRIMARY KEY,
  ip_address VARCHAR(64),
  expires_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS proxy_privileges (
  player_uuid VARCHAR(36) PRIMARY KEY,
  primary_group VARCHAR(64) NOT NULL,
  temp_group VARCHAR(64),
  temp_expires_at BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS proxy_privilege_audit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor TEXT NOT NULL,
  target_uuid TEXT NOT NULL,
  target_name TEXT NOT NULL,
  action TEXT NOT NULL,
  detail TEXT NOT NULL,
  created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS proxy_punishments (
  id VARCHAR(64) PRIMARY KEY,
  target_uuid VARCHAR(36) NOT NULL,
  actor_name VARCHAR(64),
  type VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64),
  reason_title TEXT NOT NULL,
  reason_description TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  active INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mark all migrations as already applied
INSERT IGNORE INTO fx_schema_history (version, description, applied_at) VALUES
(1, 'core_base', UNIX_TIMESTAMP() * 1000),
(2, 'economy_base', UNIX_TIMESTAMP() * 1000),
(3, 'kits_base', UNIX_TIMESTAMP() * 1000),
(4, 'shop_base', UNIX_TIMESTAMP() * 1000),
(5, 'auction_base', UNIX_TIMESTAMP() * 1000),
(6, 'moderation_base', UNIX_TIMESTAMP() * 1000),
(7, 'security_base', UNIX_TIMESTAMP() * 1000),
(8, 'custom_items_base', UNIX_TIMESTAMP() * 1000),
(9, 'retention_base', UNIX_TIMESTAMP() * 1000),
(10, 'store_base', UNIX_TIMESTAMP() * 1000),
(11, 'player_shops_base', UNIX_TIMESTAMP() * 1000),
(12, 'custom_items_rewards', UNIX_TIMESTAMP() * 1000),
(13, 'store_lifecycle', UNIX_TIMESTAMP() * 1000),
(14, 'retention_community', UNIX_TIMESTAMP() * 1000),
(15, 'ranks_base', UNIX_TIMESTAMP() * 1000),
(16, 'auth_base', UNIX_TIMESTAMP() * 1000),
(17, 'auth_uuid_index', UNIX_TIMESTAMP() * 1000),
(18, 'player_flow_entry_points', UNIX_TIMESTAMP() * 1000),
(19, 'guilds_base', UNIX_TIMESTAMP() * 1000),
(20, 'guilds_war_prep', UNIX_TIMESTAMP() * 1000),
(21, 'guilds_features', UNIX_TIMESTAMP() * 1000),
(22, 'guild_levels_rewards', UNIX_TIMESTAMP() * 1000),
(23, 'guild_tag_and_progress', UNIX_TIMESTAMP() * 1000),
(24, 'item_templates', UNIX_TIMESTAMP() * 1000),
(25, 'regions', UNIX_TIMESTAMP() * 1000),
(26, 'regions_display_prefs', UNIX_TIMESTAMP() * 1000),
(27, 'kit_definitions', UNIX_TIMESTAMP() * 1000),
(28, 'donate_shop', UNIX_TIMESTAMP() * 1000),
(29, 'progression_quest_shop', UNIX_TIMESTAMP() * 1000),
(30, 'quest_objective_progress', UNIX_TIMESTAMP() * 1000),
(31, 'guild_level_objectives', UNIX_TIMESTAMP() * 1000),
(32, 'guild_quest_chain_reset', UNIX_TIMESTAMP() * 1000),
(33, 'custom_crafts', UNIX_TIMESTAMP() * 1000),
(35, 'item_template_flags', UNIX_TIMESTAMP() * 1000),
(36, 'modern_furnace', UNIX_TIMESTAMP() * 1000),
(37, 'craft_sulfur_info', UNIX_TIMESTAMP() * 1000),
(40, 'sleepers_base', UNIX_TIMESTAMP() * 1000),
(41, 'sleepers_visual_and_health', UNIX_TIMESTAMP() * 1000),
(42, 'cases', UNIX_TIMESTAMP() * 1000);

SET FOREIGN_KEY_CHECKS=1;