const mysql = require('mysql2/promise');
const config = require('../../config');

let pool = null;
let dbConnected = false;

async function initDatabase() {
  try {
    pool = mysql.createPool(config.database);
    await pool.query('SELECT 1');
    await createSiteTables();
    await createCompatibilityViews();
    dbConnected = true;
    console.log('MySQL база данных готова');
  } catch (err) {
    dbConnected = false;
    console.error('Ошибка MySQL:', err.message);
    throw err;
  }
}

function isConnected() {
  return dbConnected;
}

async function createSiteTables() {
  const tables = [
    `CREATE TABLE IF NOT EXISTS foxaria_news (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      title TEXT NOT NULL,
      content LONGTEXT NOT NULL,
      preview TEXT,
      image TEXT,
      server VARCHAR(64) DEFAULT 'all',
      tags TEXT,
      published TINYINT(1) DEFAULT 1,
      pinned TINYINT(1) DEFAULT 0,
      views INT DEFAULT 0,
      author VARCHAR(128),
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_shop_categories (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR(255) NOT NULL,
      icon VARCHAR(64) DEFAULT '?',
      sort_order INT DEFAULT 0,
      visible TINYINT(1) DEFAULT 1
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_shop_items (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      category_id INT,
      name VARCHAR(255) NOT NULL,
      description TEXT,
      image TEXT,
      price DECIMAL(20, 2) NOT NULL,
      original_price DECIMAL(20, 2),
      currency VARCHAR(16) DEFAULT 'RUB',
      commands LONGTEXT,
      server_id VARCHAR(64) DEFAULT 'all',
      item_type VARCHAR(64) DEFAULT 'donate',
      featured TINYINT(1) DEFAULT 0,
      sort_order INT DEFAULT 0,
      visible TINYINT(1) DEFAULT 1,
      CONSTRAINT fk_foxaria_shop_items_category
        FOREIGN KEY (category_id) REFERENCES foxaria_shop_categories(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_payments (
      id VARCHAR(64) PRIMARY KEY,
      player_uuid VARCHAR(36),
      player_name VARCHAR(128),
      item_id INT,
      item_name VARCHAR(255),
      amount DECIMAL(20, 2) NOT NULL,
      currency VARCHAR(16) DEFAULT 'RUB',
      payment_method VARCHAR(64),
      provider_id VARCHAR(128),
      status VARCHAR(32) DEFAULT 'pending',
      commands_executed TINYINT(1) DEFAULT 0,
      metadata LONGTEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      completed_at DATETIME NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_staff (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR(128) NOT NULL,
      role VARCHAR(128) NOT NULL,
      role_color VARCHAR(32) DEFAULT '#FF6B35',
      avatar TEXT,
      discord VARCHAR(255),
      vk VARCHAR(255),
      telegram VARCHAR(255),
      description TEXT,
      sort_order INT DEFAULT 0,
      active TINYINT(1) DEFAULT 1
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_wipes (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      server_id VARCHAR(64) NOT NULL,
      server_name VARCHAR(128) NOT NULL,
      wipe_date DATETIME NOT NULL,
      description TEXT,
      wipe_type VARCHAR(32) DEFAULT 'full',
      completed TINYINT(1) DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_settings (
      \`key\` VARCHAR(191) PRIMARY KEY,
      value LONGTEXT,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_action_logs (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      actor VARCHAR(128),
      action VARCHAR(128),
      target VARCHAR(255),
      details LONGTEXT,
      ip VARCHAR(64),
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    `CREATE TABLE IF NOT EXISTS foxaria_pending_commands (
      id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
      payment_id VARCHAR(64) NOT NULL,
      player_name VARCHAR(128) NOT NULL,
      server_id VARCHAR(64) NOT NULL,
      commands LONGTEXT NOT NULL,
      attempts INT DEFAULT 0,
      last_attempt_at DATETIME NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
  ];

  for (const sql of tables) {
    await pool.query(sql);
  }
}

async function createCompatibilityViews() {
  const views = [
    ['luckperms_players',
      `SELECT pa.player_uuid AS uuid, pa.username, COALESCE(pp.primary_group, 'default') AS primary_group
       FROM proxy_accounts pa
       LEFT JOIN proxy_privileges pp ON pp.player_uuid = pa.player_uuid`],
    ['luckperms_user_permissions',
      `SELECT id, player_uuid AS uuid, permission,
              CAST(active AS SIGNED) AS value, COALESCE(expires_at, 0) AS expiry
       FROM fx_permission_grants`],
    ['litebans_bans',
      `SELECT p.id, p.target_uuid AS uuid, p.reason,
              COALESCE(a.name, 'Консоль') AS banned_by_name,
              NULL AS server,
              p.expires_at AS \`until\`,
              p.created_at AS time,
              CAST(p.active AS SIGNED) AS active,
              0 AS ipban, 0 AS silent
       FROM fx_punishments p
       LEFT JOIN fx_players a ON a.uuid = p.actor_uuid
       WHERE LOWER(p.type) IN ('ban', 'tempban')
       UNION ALL
       SELECT pr.id, pr.target_uuid AS uuid,
              CASE
                WHEN TRIM(COALESCE(pr.reason_description, '')) = '' THEN COALESCE(pr.reason_title, pr.reason_code, 'Причина не указана')
                ELSE CONCAT(TRIM(COALESCE(pr.reason_title, '')), ': ', TRIM(COALESCE(pr.reason_description, '')))
              END AS reason,
              COALESCE(pr.actor_name, 'Консоль') AS banned_by_name,
              NULL AS server,
              pr.expires_at AS \`until\`,
              pr.created_at AS time,
              CAST(pr.active AS SIGNED) AS active,
              0 AS ipban, 0 AS silent
       FROM proxy_punishments pr
       WHERE LOWER(pr.type) IN ('ban', 'tempban')`],
    ['litebans_mutes',
      `SELECT p.id, p.target_uuid AS uuid, p.reason,
              COALESCE(a.name, 'Консоль') AS banned_by_name,
              NULL AS server,
              p.expires_at AS \`until\`,
              p.created_at AS time,
              CAST(p.active AS SIGNED) AS active
       FROM fx_punishments p
       LEFT JOIN fx_players a ON a.uuid = p.actor_uuid
       WHERE LOWER(p.type) IN ('mute', 'tempmute')
       UNION ALL
       SELECT pr.id, pr.target_uuid AS uuid,
              CASE
                WHEN TRIM(COALESCE(pr.reason_description, '')) = '' THEN COALESCE(pr.reason_title, pr.reason_code, 'Причина не указана')
                ELSE CONCAT(TRIM(COALESCE(pr.reason_title, '')), ': ', TRIM(COALESCE(pr.reason_description, '')))
              END AS reason,
              COALESCE(pr.actor_name, 'Консоль') AS banned_by_name,
              NULL AS server,
              pr.expires_at AS \`until\`,
              pr.created_at AS time,
              CAST(pr.active AS SIGNED) AS active
       FROM proxy_punishments pr
       WHERE LOWER(pr.type) IN ('mute', 'tempmute')`],
    ['litebans_warnings',
      `SELECT p.id, p.target_uuid AS uuid, p.reason,
              COALESCE(a.name, 'Консоль') AS banned_by_name,
              p.created_at AS time,
              CAST(p.active AS SIGNED) AS active
       FROM fx_punishments p
       LEFT JOIN fx_players a ON a.uuid = p.actor_uuid
       WHERE UPPER(p.type) IN ('WARN', 'TEMPWARN')`],
    ['litebans_history',
      `SELECT uuid, name, last_seen_at AS date FROM fx_players
       UNION ALL
       SELECT pr.target_uuid AS uuid,
              COALESCE(MAX(NULLIF(TRIM(pr.target_name), '')), 'Неизвестно') AS name,
              MAX(pr.created_at) AS date
       FROM proxy_punishments pr
       WHERE LOWER(pr.type) IN ('ban', 'tempban', 'mute', 'tempmute')
         AND NOT EXISTS (SELECT 1 FROM fx_players fp WHERE LOWER(fp.uuid) = LOWER(pr.target_uuid))
       GROUP BY pr.target_uuid`],
    ['guilds',
      `SELECT id, name, owner_uuid, motd AS description, level AS tier,
              bank_balance, guild_coins, guild_points, chest_rows, shop_tier,
              member_slots_bonus, friendly_fire, tag_color, created_at
       FROM fx_guilds`],
    ['guild_members',
      `SELECT guild_id, player_uuid AS uuid, player_name AS username, role, joined_at
       FROM fx_guild_members`],
    ['fx_regions_view',
      `SELECT id, world, center_x, center_z, half_size, level, owner_uuid,
              cabinet_x, cabinet_y, cabinet_z, core_hp, core_max_hp,
              deposited_wood, deposited_iron, flags, display_name
       FROM fx_regions`],
    ['fx_region_members_view',
      `SELECT region_id, member_uuid, role FROM fx_region_members`],
    ['playtime',
      `SELECT uuid, playtime_seconds AS time FROM fx_players`],
    ['foxaria_auth',
      `SELECT player_uuid, username, password_hash, password_salt AS salt, NULL AS iterations
       FROM proxy_accounts`],
    ['economy_accounts',
      `SELECT player_uuid, balance, tokens FROM fx_economy_accounts`],
    ['player_homes',
      `SELECT ROW_NUMBER() OVER (ORDER BY player_uuid, home_name) AS id,
              player_uuid, home_name, world, x, y, z, yaw, pitch
       FROM fx_player_homes`],
    ['login_streaks',
      `SELECT player_uuid, streak_days, last_login_date FROM fx_login_streaks`],
    ['player_progression',
      `SELECT player_uuid, knowledge_level, current_quest_id, quest_progress,
              completed_quests, quest_objective_csv
       FROM fx_player_progression`],
    ['guild_stats',
      `SELECT guild_id, total_kills FROM fx_guild_stats`],
  ];

  for (const [name, select] of views) {
    try {
      await pool.query(`DROP VIEW IF EXISTS \`${name}\``);
      await pool.query(`CREATE OR REPLACE VIEW \`${name}\` AS ${select}`);
    } catch (err) {
      console.warn(`[views] Пропущен view '${name}': ${err.message}`);
    }
  }
}

function adaptSql(sql) {
  return String(sql)
    .replace(/datetime\('now',\s*'-30 days'\)/gi, 'DATE_SUB(NOW(), INTERVAL 30 DAY)')
    .replace(/datetime\('now'\)/gi, 'NOW()')
    .replace(/\b_proxy\./g, '')
    .replace(/\b_fx\./g, '');
}

async function query(sql, params = []) {
  if (!dbConnected) throw new Error('База данных недоступна');
  const [rows] = await pool.execute(adaptSql(sql), params);
  return rows;
}

async function queryOne(sql, params = []) {
  const rows = await query(sql, params);
  return Array.isArray(rows) ? (rows[0] ?? null) : rows;
}

async function logAction(actor, action, target, details, ip) {
  try {
    await query(
      'INSERT INTO foxaria_action_logs (actor, action, target, details, ip) VALUES (?, ?, ?, ?, ?)',
      [actor, action, target, JSON.stringify(details), ip]
    );
  } catch {}
}

function getPool() {
  if (!pool) {
    throw new Error('База данных недоступна');
  }
  return {
    execute: (sql, params = []) => pool.execute(adaptSql(sql), params),
  };
}

module.exports = { initDatabase, getPool, query, queryOne, logAction, isConnected };
