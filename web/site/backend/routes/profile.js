const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { authMiddleware } = require('../middleware/auth');
const config = require('../../config');

// GET /api/profile/:username
router.get('/:username', authMiddleware, async (req, res) => {
  try {
    const { username } = req.params;
    const lpCfg = config.plugins.luckperms;
    const litebans = config.plugins.litebans;

    const player = await queryOne(
      `SELECT uuid, username, primary_group FROM ${lpCfg.playersTable} WHERE LOWER(username) = LOWER(?) LIMIT 1`,
      [username]
    );
    if (!player) return res.status(404).json({ error: 'Игрок не найден' });

    // Profiles are public to authenticated players, but sensitive data is hidden for non-owners.
    const isOwnProfile = player.uuid === req.user.uuid || req.user.isAdmin;

    const uuid = player.uuid;
    const group = player.primary_group || 'default';
    const guildsCfg = config.plugins.guilds;
    const gpCfg = config.plugins.griefprevention;

    const [
      banInfo, extraPerms, playtimeRow,
      economy, homes, loginStreak, progression,
      punishBans, punishMutes, purchases,
      guildMember, regions, auctionHistory, authInfo,
    ] = await Promise.all([
      // Активный бан
      queryOne(
        `SELECT reason, banned_by_name, \`until\`, time FROM ${litebans.bansTable}
         WHERE uuid = ? AND active = 1 ORDER BY time DESC LIMIT 1`,
        [uuid]
      ).catch(() => null),

      // Дополнительные права
      query(
        `SELECT permission, value, expiry FROM ${lpCfg.userPermissionsTable} WHERE uuid = ? AND value = 1`,
        [uuid]
      ).catch(() => []),

      // Время игры
      queryOne('SELECT time FROM playtime WHERE uuid = ? LIMIT 1', [uuid]).catch(() => null),

      // Экономика
      queryOne('SELECT balance, tokens FROM economy_accounts WHERE player_uuid = ? LIMIT 1', [uuid]).catch(() => null),

      // Дома
      query('SELECT id, home_name, world, x, y, z, yaw, pitch FROM player_homes WHERE player_uuid = ? ORDER BY home_name', [uuid]).catch(() => []),

      // Стрик логина (правильные колонки!)
      queryOne('SELECT streak_days, last_login_date FROM login_streaks WHERE player_uuid = ? LIMIT 1', [uuid]).catch(() => null),

      // Квестовая прогрессия
      queryOne('SELECT knowledge_level, current_quest_id, quest_progress, completed_quests FROM player_progression WHERE player_uuid = ? LIMIT 1', [uuid]).catch(() => null),

      // История банов
      query(
        `SELECT 'ban' as type, reason, banned_by_name as by, \`until\`, active, time
         FROM ${litebans.bansTable} WHERE uuid = ? ORDER BY time DESC LIMIT 10`,
        [uuid]
      ).catch(() => []),

      // История мутов
      query(
        `SELECT 'mute' as type, reason, banned_by_name as by, \`until\`, active, time
         FROM ${litebans.muteTable} WHERE uuid = ? ORDER BY time DESC LIMIT 10`,
        [uuid]
      ).catch(() => []),

      // Покупки через сайт
      query(
        'SELECT item_name, amount, payment_method, completed_at FROM foxaria_payments WHERE player_uuid = ? AND status = "completed" ORDER BY completed_at DESC LIMIT 20',
        [uuid]
      ).catch(() => []),

      // Гильдия (fx_guild_members использует player_uuid/player_name; fx_guilds использует motd/level)
      guildsCfg.enabled ? queryOne(
        `SELECT m.guild_id, m.role, m.player_name AS member_name,
                g.name AS guild_name,
                g.motd AS guild_motd,
                g.level AS guild_tier,
                g.bank_balance, g.guild_coins, g.guild_points, g.friendly_fire,
                g.chest_rows, g.shop_tier, g.tag_color
         FROM ${guildsCfg.membersTable} m
         JOIN ${guildsCfg.guildsTable} g ON g.id = m.guild_id
         WHERE m.player_uuid = ? LIMIT 1`,
        [uuid]
      ).catch(() => null) : Promise.resolve(null),

      // Регионы/приваты Foxaria
      query(
        `SELECT r.id, r.world, r.display_name, r.center_x, r.center_z, r.half_size,
                r.level, r.core_hp, r.core_max_hp, r.deposited_wood, r.deposited_iron, r.flags
         FROM fx_regions_view r
         WHERE r.owner_uuid = ?`,
        [uuid]
      ).catch(() => []),

      // История аукциона
      query(
        `SELECT seller_uuid, price, fee, status, created_at
         FROM fx_auction_listings
         WHERE seller_uuid = ? OR buyer_uuid = ?
         ORDER BY created_at DESC LIMIT 10`,
        [uuid, uuid]
      ).catch(() => []),

      // Дата регистрации из auth
      queryOne('SELECT registered_at, last_login_at FROM foxaria_auth WHERE last_uuid = ? LIMIT 1', [uuid]).catch(() => null),
    ]);

    const playtime = playtimeRow?.time || 0;
    const punishments = [...punishBans, ...punishMutes].sort((a, b) => (b.time || 0) - (a.time || 0));

    // Обработка гильдии с участниками
    let guild = null;
    if (guildMember) {
      const [guildMembers, guildStats] = await Promise.all([
        query(
          `SELECT player_uuid AS uuid, player_name AS username, role, joined_at
           FROM ${guildsCfg.membersTable}
           WHERE guild_id = ?
           ORDER BY CASE role WHEN 'OWNER' THEN 0 WHEN 'MASTER' THEN 1 WHEN 'LEADER' THEN 2 WHEN 'OFFICER' THEN 3 WHEN 'ADMIN' THEN 4 ELSE 5 END`,
          [guildMember.guild_id]
        ).catch(() => []),
        queryOne('SELECT total_kills FROM fx_guild_stats WHERE guild_id = ? LIMIT 1', [guildMember.guild_id]).catch(() => null),
      ]);

      guild = {
        id: guildMember.guild_id,
        name: guildMember.guild_name,
        motd: guildMember.guild_motd,
        tier: guildMember.guild_tier || 1,
        bankBalance: parseFloat(guildMember.bank_balance) || 0,
        guildCoins: guildMember.guild_coins || 0,
        guildPoints: guildMember.guild_points || 0,
        friendlyFire: !!guildMember.friendly_fire,
        tagColor: guildMember.tag_color || 'WHITE',
        chestRows: guildMember.chest_rows || 3,
        shopTier: guildMember.shop_tier || 1,
        myRole: guildMember.role,
        isLeader: ['OWNER', 'MASTER', 'LEADER'].includes(guildMember.role),
        isOfficer: ['OFFICER', 'ADMIN'].includes(guildMember.role),
        totalKills: guildStats?.total_kills || 0,
        members: guildMembers.map(m => ({
          uuid: m.uuid,
          username: m.username || 'Неизвестно',
          role: m.role,
          joinedAt: m.joined_at,
          avatar: `https://mc-heads.net/avatar/${m.username || 'Steve'}/40`,
        })),
      };
    }

    // Обработка регионов
    const processedRegions = await Promise.all((regions || []).map(async r => {
      const members = await query(
        'SELECT m.member_uuid, m.role, p.username FROM fx_region_members_view m LEFT JOIN luckperms_players p ON p.uuid = m.member_uuid WHERE m.region_id = ?',
        [r.id]
      ).catch(() => []);
      return {
        id: r.id,
        displayName: r.display_name || `Регион #${r.id}`,
        world: r.world || 'world',
        centerX: r.center_x,
        centerZ: r.center_z,
        halfSize: r.half_size,
        level: r.level || 1,
        coreHp: r.core_hp || 0,
        coreMaxHp: r.core_max_hp || 100,
        depositedWood: r.deposited_wood || 0,
        depositedIron: r.deposited_iron || 0,
        size: r.half_size ? (r.half_size * 2 + 1) * (r.half_size * 2 + 1) : 0,
        members: members.map(m => ({
          uuid: m.member_uuid,
          username: m.username || 'Неизвестно',
          role: m.role,
        })),
      };
    }));

    // Обработка квестовой прогрессии
    const completedQuestCount = progression?.completed_quests
      ? progression.completed_quests.split(/[,;]/).filter(q => q.trim()).length
      : 0;

    const isAdmin = lpCfg.adminGroups.includes(group.toLowerCase());
    const isMod = lpCfg.modGroups.includes(group.toLowerCase());
    const isDonate = lpCfg.donateGroups.includes(group.toLowerCase());

    res.json({
      uuid,
      username: player.username,
      avatar: `https://mc-heads.net/avatar/${player.username}/100`,
      skin: `https://mc-heads.net/body/${player.username}/100`,
      group,
      isAdmin,
      isMod,
      isDonate,
      donateLabel: isDonate ? group.toUpperCase() : isAdmin ? 'Администратор' : isMod ? 'Модератор' : 'Игрок',

      // Дата регистрации
      registeredAt: authInfo?.registered_at ? new Date(authInfo.registered_at).toISOString() : null,
      lastLoginAt: authInfo?.last_login_at ? new Date(authInfo.last_login_at).toISOString() : null,

      banInfo: banInfo ? {
        reason: banInfo.reason,
        bannedBy: banInfo.banned_by_name,
        until: banInfo.until > 0 ? new Date(banInfo.until).toISOString() : null,
        bannedAt: new Date(banInfo.time).toISOString(),
        permanent: !banInfo.until || banInfo.until <= 0,
      } : null,

      playtime: {
        seconds: playtime,
        hours: Math.floor(playtime / 3600),
        formatted: formatPlaytime(playtime),
      },

      economy: {
        balance: economy?.balance ?? 0,
        tokens: economy?.tokens ?? 0,
      },

      loginStreak: {
        current: loginStreak?.streak_days || 0,
        lastLoginDate: loginStreak?.last_login_date || null,
      },

      progression: {
        knowledgeLevel: progression?.knowledge_level || 1,
        currentQuestId: progression?.current_quest_id || null,
        questProgress: progression?.quest_progress || 0,
        completedQuestCount,
      },

      // Homes and regions contain base coordinates — only visible to the owner and admins.
      homes: isOwnProfile ? (homes || []).map(h => ({
        id: h.id,
        name: h.home_name || 'home',
        world: h.world || 'world',
        x: Math.round(h.x * 10) / 10,
        y: Math.round(h.y * 10) / 10,
        z: Math.round(h.z * 10) / 10,
        yaw: h.yaw,
        pitch: h.pitch,
      })) : [],

      regions: isOwnProfile ? processedRegions : [],

      guild,

      auctionHistory: (auctionHistory || []).map(a => ({
        price: parseFloat(a.price) || 0,
        fee: parseFloat(a.fee) || 0,
        status: a.status,
        isSeller: a.seller_uuid === uuid,
        createdAt: a.created_at ? new Date(a.created_at).toISOString() : null,
      })),

      extraPerms: extraPerms.map(p => p.permission),
      punishments: punishments.map(p => ({
        type: p.type,
        reason: p.reason || 'Причина не указана',
        by: p.by || 'Консоль',
        until: p.until > 0 ? new Date(p.until).toISOString() : null,
        bannedAt: p.time > 0 ? new Date(p.time).toISOString() : null,
        permanent: !p.until || p.until <= 0,
        active: !!p.active,
      })),
      purchases,

      // Список доступных серверов для селектора
      servers: config.servers.map(s => ({ id: s.id, name: s.name, icon: s.icon })),
    });
  } catch (err) {
    console.error('Profile error:', err);
    res.status(500).json({ error: 'Ошибка загрузки профиля' });
  }
});

function formatPlaytime(seconds) {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days}д ${hours}ч ${minutes}м`;
  if (hours > 0) return `${hours}ч ${minutes}м`;
  return `${minutes}м`;
}

module.exports = router;
